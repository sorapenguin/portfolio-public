(() => {
  "use strict";

  const state = {
    currentLab: null,
    selectedOptions: {},
    runResult: null,
    dirty: false,
    module: null,
    mode: "guided",
    stage: 0,
    completed: new Set(),
    completedExperiments: new Set(),
    controlFields: new Map(),
    ranDifferentSelectionForStage: false,
    activeWorkspaceView: "settings",
    running: false,
    nextStageAvailable: false
  };

  const $ = id => document.getElementById(id);
  const text = (tag, value) => {
    const element = document.createElement(tag);
    element.textContent = value || "";
    return element;
  };

  async function api(path) {
    const response = await fetch(path, { headers: { Accept: "application/json" } });
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json") ? await response.json() : null;
    if (!response.ok) throw new Error(body && body.error && body.error.code || "request_failed");
    return body;
  }

  function setError(message) {
    const element = $("error");
    element.hidden = !message;
    element.textContent = message || "";
  }

  function setLoading(value) {
    $("app").setAttribute("aria-busy", String(value));
    $("loading").hidden = !value;
  }

  function list(root, items) {
    root.replaceChildren(...(items || []).map(item => text("li", item)));
  }

  function labelFor(lab, id) {
    for (const node of lab.topology.nodes) {
      if (node.id === id) return node.display.label;
      for (const policy of node.display.attached_policies || []) {
        if (policy.node_id === id) return policy.label;
      }
    }
    return id;
  }

  function displayFor(group, value, fallback) {
    const keys = { nodeStates: "node_states", events: "events", outcomes: "outcomes", termination: "termination" };
    const key = keys[group] || group;
    return state.module && state.module.display && state.module.display[key] && state.module.display[key][value] || fallback;
  }

  function matches(selection, expected) {
    const expectedEntries = Object.entries(expected || {});
    const selectionEntries = Object.entries(selection || {});
    return expectedEntries.length === selectionEntries.length && expectedEntries.every(([controlID, optionID]) => selection[controlID] === optionID);
  }

  function matchingExperiment(stage) {
    return (stage.experiments || []).find(experiment => matches(state.selectedOptions, experiment.recommended_selection)) || null;
  }
  function stageComplete(stage) { return stage.run_requirement === "none" || (stage.experiments || []).every(experiment => state.completedExperiments.has(experiment.experiment_id)); }

  function validateModule(lab, module) {
    if (!module) return false;
    const controls = new Map(lab.controls.map(control => [control.id, new Set(control.options.map(option => option.id))]));
    if (!Object.entries(module.controls).every(([controlID, copy]) => controls.has(controlID) && Object.keys(copy.options).every(optionID => controls.get(controlID).has(optionID)))) return false;
    return module.stages.every(stage => (stage.experiments || []).every(experiment => Object.entries(experiment.recommended_selection || {}).every(([controlID, optionID]) => controls.has(controlID) && controls.get(controlID).has(optionID))));
  }

  function renderList(data) {
    const root = $("lab-list");
    root.replaceChildren();
    if (!data.labs.length) {
      root.append(text("p", "利用可能なLabはありません。"));
      return;
    }
    for (const lab of data.labs) {
      const card = document.createElement("article");
      card.className = "lab-card";
      card.append(text("h2", lab.title), text("p", `${lab.provider} ・ ${lab.difficulty} ・ ${lab.mode}`));
      const link = text("a", "詳細を見る");
      link.href = "/labs/" + encodeURIComponent(lab.id);
      card.append(link);
      root.append(card);
    }
  }

  function renderTopology(lab, result) {
    const root = $("topology");
    root.replaceChildren();
    const byID = new Map((result && result.node_states || []).map(item => [item.node_id, item.state]));
    const origin = result && result.response_origin_node_id;
    lab.topology.nodes.forEach((node, index) => {
      const box = document.createElement("article");
      box.className = "topology-node";
      const nodeState = byID.get(node.id);
      if (nodeState) box.dataset.state = nodeState;
      if (node.id === origin) box.dataset.origin = "true";
      box.append(
        text("strong", node.display.label),
        text("p", nodeState ? `状態: ${displayFor("nodeStates", nodeState, "未対応の状態")}` : "状態: 未実行")
      );
      if (node.id === origin) box.append(text("p", "ここで応答を生成"));
      for (const policy of node.display.attached_policies || []) {
        const badge = text("span", policy.label);
        badge.className = "policy";
        if (policy.node_id === origin) badge.dataset.origin = "true";
        box.append(badge);
      }
      root.append(box);
      if (index < lab.topology.nodes.length - 1) {
        const arrow = text("div", "→");
        arrow.className = "topology-arrow";
        arrow.setAttribute("aria-hidden", "true");
        root.append(arrow);
      }
    });
  }

  function renderRoles() {
    const root = $("service-roles");
    root.replaceChildren();
    for (const role of state.module.service_roles) {
      const card = document.createElement("article");
      card.className = "service-role";
      card.append(text("h3", role.label), text("p", role.description));
      root.append(card);
    }
  }

  function ensureLayout() {
    const learning = $("learning");
    if (!$("guided-layout")) {
      const layout = document.createElement("div");
      layout.id = "guided-layout";
      layout.className = "guided-layout";
      const workspace = document.createElement("div");
      workspace.id = "experiment-workspace";
      workspace.className = "experiment-workspace";
      learning.after(layout);
      layout.append($("guided-panel"), $("free-panel"), workspace);

      const stageDetails = document.createElement("details");
      stageDetails.id = "stage-details";
      stageDetails.className = "stage-details";
      stageDetails.append(text("summary", "このStageの説明を見る"), $("stage-purpose"), $("stage-question").parentElement, $("stage-after"));
      $("stage-setting").after(stageDetails);

      const detail = $("detail-view");
      const objectives = $("objectives");
      const objectiveTitle = objectives.previousElementSibling;
      const overview = document.createElement("details");
      overview.id = "lab-overview";
      const summary = text("summary", "ラボの概要を見る");
      overview.append(summary);
      if (objectiveTitle) overview.append(objectiveTitle);
      overview.append(objectives, $("learning-title"), $("learning-introduction-text"), $("service-roles"));
      learning.prepend(overview);

      const simplification = Array.from(detail.children).find(child => child.tagName === "SECTION" && child.querySelector("#simplifications"));
      const topology = $("topology").parentElement;
      const controls = $("controls").parentElement;
      const workspaceTabs = document.createElement("div");
      workspaceTabs.id = "workspace-tabs";
      workspaceTabs.className = "workspace-tabs";
      workspaceTabs.setAttribute("role", "tablist");
      workspaceTabs.setAttribute("aria-label", "実験の表示を選ぶ");
      const settingsTab = text("button", "設定");
      settingsTab.id = "workspace-settings";
      settingsTab.type = "button";
      settingsTab.setAttribute("role", "tab");
      settingsTab.setAttribute("aria-controls", "settings-pane");
      const resultTab = text("button", "結果");
      resultTab.id = "workspace-result";
      resultTab.type = "button";
      resultTab.setAttribute("role", "tab");
      resultTab.setAttribute("aria-controls", "result-pane");
      workspaceTabs.append(settingsTab, resultTab);

      const settingsPane = document.createElement("section");
      settingsPane.id = "settings-pane";
      settingsPane.className = "workspace-pane settings-pane";
      settingsPane.tabIndex = -1;
      settingsPane.setAttribute("role", "tabpanel");
      settingsPane.setAttribute("aria-labelledby", "workspace-settings");
      const resultPane = document.createElement("section");
      resultPane.id = "result-pane";
      resultPane.className = "workspace-pane result-pane";
      resultPane.tabIndex = -1;
      resultPane.setAttribute("role", "tabpanel");
      resultPane.setAttribute("aria-labelledby", "workspace-result");
      const resultEmpty = text("p", "設定後に仮想テストを実行すると、ここに結果が表示されます。");
      resultEmpty.id = "result-empty";
      resultEmpty.className = "result-empty";
      resultEmpty.setAttribute("role", "status");
      resultPane.append(resultEmpty, $("result"));
      settingsPane.append(simplification, topology, controls);
      workspace.append(workspaceTabs, settingsPane, resultPane);

      const actions = document.createElement("div");
      actions.id = "mobile-actions";
      actions.className = "mobile-actions";
      const mobileApply = text("button", "この実験の設定を反映");
      mobileApply.id = "mobile-apply-stage";
      mobileApply.type = "button";
      mobileApply.className = "secondary-action";
      const mobileRun = text("button", "仮想テストを実行");
      mobileRun.id = "mobile-run";
      mobileRun.type = "button";
      mobileRun.className = "primary-action";
      const backToSettings = text("button", "設定へ戻る");
      backToSettings.id = "back-to-settings";
      backToSettings.type = "button";
      backToSettings.className = "secondary-action";
      const mobileNext = text("button", "次の実験へ進む");
      mobileNext.id = "mobile-next-stage";
      mobileNext.type = "button";
      mobileNext.className = "primary-action";
      actions.append(mobileApply, mobileRun, backToSettings, mobileNext);
      layout.after(actions);
      settingsTab.addEventListener("click", () => setWorkspaceView("settings"));
      resultTab.addEventListener("click", () => setWorkspaceView("result"));
      backToSettings.addEventListener("click", () => setWorkspaceView("settings", true));
      mobileApply.addEventListener("click", applyStage);
      mobileRun.addEventListener("click", runSimulation);
      mobileNext.addEventListener("click", goNext);
    }
  }

  function isMobile() {
    return window.matchMedia("(max-width: 699px)").matches;
  }

  function isDesktop() {
    return window.matchMedia("(min-width: 1100px)").matches;
  }

  function resetPaneScroll() {
    $("settings-pane").scrollTop = 0;
    $("result-pane").scrollTop = 0;
  }

  function setWorkspaceView(view, focus) {
    state.activeWorkspaceView = view;
    const desktop = isDesktop();
    const settings = $("settings-pane");
    const result = $("result-pane");
    const settingsTab = $("workspace-settings");
    const resultTab = $("workspace-result");
    settings.hidden = !desktop && view !== "settings";
    result.hidden = !desktop && view !== "result";
    settingsTab.setAttribute("aria-selected", String(view === "settings"));
    resultTab.setAttribute("aria-selected", String(view === "result"));
    updateMobileActions();
    if (focus && !desktop) {
      (view === "settings" ? $("controls") : $("result-pane")).focus({ preventScroll: false });
    }
  }

  function updateMobileActions() {
    if (!$("mobile-actions")) return;
    const stage = state.module ? currentStage() : null;
    const primaryExperiment = stage && stage.experiments && stage.experiments[0];
    const mobile = isMobile();
    const actions = $("mobile-actions");
    const apply = $("mobile-apply-stage");
    const run = $("mobile-run");
    const next = $("mobile-next-stage");
    const back = $("back-to-settings");
    const desktopApply = $("apply-stage");
    const desktopRun = $("run");
    const desktopNext = $("next-stage");
    if (!mobile) {
      actions.hidden = true;
      desktopApply.hidden = false;
      desktopRun.hidden = false;
      desktopNext.hidden = !state.nextStageAvailable;
      return;
    }
    actions.hidden = false;
    desktopApply.hidden = true;
    desktopRun.hidden = true;
    desktopNext.hidden = true;
    apply.hidden = true;
    run.hidden = true;
    back.hidden = true;
    next.hidden = true;
    apply.disabled = state.running;
    run.disabled = state.running || !validateSelections().valid;
    run.textContent = state.running ? "実行中…" : state.runResult || state.dirty ? "もう一度実行" : "仮想テストを実行";
    if (state.activeWorkspaceView === "result" && state.runResult) {
      back.hidden = false;
      if (state.mode === "guided" && state.nextStageAvailable) {
        next.hidden = false;
        next.textContent = $("next-stage").textContent;
      } else if (state.mode === "guided" && primaryExperiment) {
        apply.hidden = false;
        apply.textContent = "推奨設定を反映";
      } else {
        run.hidden = false;
      }
      return;
    }
    if (state.mode === "guided" && stage && stage.run_requirement === "none") {
      apply.hidden = false;
      apply.textContent = stage.apply_label;
      return;
    }
    if (state.mode === "guided" && primaryExperiment) {
      apply.hidden = false;
      apply.textContent = stage.apply_label;
    }
    run.hidden = false;
  }

  function currentStage() {
    return state.module.stages[state.stage];
  }

  function resetResult(showStale) {
    state.runResult = null;
    state.dirty = Boolean(showStale);
    state.nextStageAvailable = false;
    $("result").hidden = true;
    $("dirty").hidden = !state.dirty;
    $("result-facts").replaceChildren();
    $("events").replaceChildren();
    $("states").replaceChildren();
    $("technical-details").replaceChildren();
    $("result-empty").hidden = false;
    $("result-empty").textContent = showStale ? "設定が変更されました。仮想テストを再実行してください。" : "設定後に仮想テストを実行すると、ここに結果が表示されます。";
    renderTopology(state.currentLab, null);
  }

  function renderControls() {
    const form = $("controls");
    form.replaceChildren();
    const stage = state.module ? currentStage() : null;
    const focusIDs = state.mode === "guided" && stage && stage.focus_control_ids ? new Set(stage.focus_control_ids) : null;
    const focused = [];
    const other = [];
    for (const control of state.currentLab.controls) {
      const field = state.controlFields.get(control.id);
      const entry = { control, field };
      if (focusIDs && !focusIDs.has(control.id)) other.push(entry);
      else focused.push(entry);
    }
    const appendControl = entry => {
      entry.field.classList.toggle("focus-control", Boolean(focusIDs && focusIDs.has(entry.control.id)));
      form.append(entry.field);
    };
    if (isMobile()) {
      state.currentLab.controls.forEach(control => appendControl({ control, field: state.controlFields.get(control.id) }));
    } else {
      focused.forEach(appendControl);
    }
    if (!isMobile() && other.length) {
      const details = document.createElement("details");
      details.className = "other-controls";
      details.append(text("summary", "その他の設定を変更する"));
      for (const entry of other) {
        details.append(entry.field);
      }
      form.append(details);
    }
  }

  function updateMode() {
    const guided = state.mode === "guided";
    $("guided-mode").setAttribute("aria-selected", String(guided));
    $("free-mode").setAttribute("aria-selected", String(!guided));
    $("guided-panel").hidden = !guided;
    $("free-panel").hidden = guided;
    $("guided-layout").dataset.mode = state.mode;
    if (!guided) {
      $("stage-after").hidden = true;
      $("next-stage").hidden = true;
    }
    renderControls();
    setWorkspaceView(state.activeWorkspaceView);
  }

  function renderStage() {
    const stage = currentStage();
    $("stage-progress").textContent = `Stage ${stage.order} / ${state.module.stages.length} ・ ${stageComplete(stage) ? "完了" : "現在"}`;
    $("stage-title").textContent = stage.title;
    $("stage-purpose").textContent = stage.purpose;
    $("stage-question").textContent = stage.run_requirement === "none" ? stage.purpose : (stage.experiments[0] && stage.experiments[0].pre_run_question) || "";
    $("stage-setting").textContent = stage.setting_text;
    $("apply-stage").textContent = stage.apply_label;
    $("stage-run-note").textContent = stage.run_requirement === "all_experiments" ? "このStageでは、実験ごとの推奨設定を反映してから、仮想テストを実行してください。" : "";
    $("stage-after").hidden = true;
    $("next-stage").hidden = true;
    $("lab-overview").open = state.stage === 0;
    $("stage-details").open = !isMobile();
    state.nextStageAvailable = false;
    updateMobileActions();
  }

  function renderLearning() {
    if (!state.module) {
      $("learning").hidden = true;
      return;
    }
    $("learning").hidden = false;
    $("learning-introduction-text").textContent = state.module.introduction;
    renderRoles();
    renderStage();
    updateMode();
  }

  function controlByID(controlID) {
    return state.currentLab && state.currentLab.controls.find(control => control.id === controlID);
  }

  function validateSelections() {
    if (!state.currentLab) return { valid: false };
    for (const control of state.currentLab.controls) {
      const optionID = state.selectedOptions[control.id];
      if (typeof optionID !== "string" || !optionID || !control.options.some(option => option.id === optionID)) {
        return { valid: false, control };
      }
    }
    return { valid: true };
  }

  function setSelection(controlID, optionID, markStale = true) {
    const control = controlByID(controlID);
    if (!control || !control.options.some(option => option.id === optionID)) return false;
    state.selectedOptions[controlID] = optionID;
    document.querySelectorAll(`input[data-control="${controlID}"]`).forEach(input => {
      input.checked = input.value === optionID;
    });
    if (markStale) markDirty();
    return true;
  }

  function updateRun() {
    const invalid = !validateSelections().valid;
    $("run").disabled = state.running || invalid;
    updateMobileActions();
  }

  function markDirty() {
    if (state.runResult) resetResult(true);
    state.activeWorkspaceView = "settings";
    setWorkspaceView("settings");
    updateRun();
  }

  function applyStage() {
    const stage = currentStage();
    const experiment = stage.experiments && stage.experiments.find(item => !state.completedExperiments.has(item.experiment_id)) || stage.experiments && stage.experiments[0];
    if (!experiment) {
      if (state.stage === state.module.stages.length - 1) {
        resetResult(false);
        state.mode = "free";
        updateMode();
        $("free-mode").focus();
      } else {
        goNext();
      }
      return;
    }
    for (const [controlID, optionID] of Object.entries(experiment.recommended_selection)) {
      if (!setSelection(controlID, optionID, false)) {
        setError("このStageの推奨設定を適用できません。Lab定義を確認してください。");
        updateMobileActions();
        return;
      }
    }
    state.ranDifferentSelectionForStage = false;
    markDirty();
    renderControls();
    updateMobileActions();
  }

  function goNext() {
    if (state.stage < state.module.stages.length - 1) {
      const nextIndex = state.module.stages.findIndex(item => item.stage_id === currentStage().next_stage_id);
      state.stage = nextIndex >= 0 ? nextIndex : state.stage + 1;
      state.ranDifferentSelectionForStage = false;
      resetResult(false);
      state.activeWorkspaceView = "settings";
      renderStage();
      renderControls();
      resetPaneScroll();
      setWorkspaceView("settings");
      $("stage-title").focus();
    } else {
      resetResult(false);
      state.mode = "free";
      updateMode();
      $("free-mode").focus();
    }
  }

  function makeControlField(control, copy) {
    const field = document.createElement("fieldset");
    field.append(text("legend", copy ? copy.label : control.label));
    const description = copy ? copy.description : control.description;
    if (description) {
      const paragraph = text("p", description);
      paragraph.className = "control-description";
      field.append(paragraph);
    }
    for (const option of control.options) {
      const optionCopy = copy && copy.options[option.id];
      const label = document.createElement("label");
      const input = document.createElement("input");
      input.type = "radio";
      input.name = "control-" + control.id;
      input.value = option.id;
      input.dataset.control = control.id;
      input.checked = option.id === control.default_option_id;
      input.addEventListener("change", () => {
        setSelection(control.id, input.value);
      });
      if (input.checked) setSelection(control.id, option.id, false);
      label.className = "option";
      label.append(input, text("strong", optionCopy ? optionCopy.label : option.label));
      const detail = optionCopy ? optionCopy.description : option.description;
      if (detail) {
        const small = text("small", detail);
        small.className = "option-description";
        label.append(small);
      }
      field.append(label);
    }
    return field;
  }

  function renderDetail(lab, module) {
    state.currentLab = lab;
    state.module = validateModule(lab, module) ? module : null;
    state.stage = 0;
    state.completed = new Set();
    state.mode = "guided";
    state.runResult = null;
    state.dirty = false;
    state.ranDifferentSelectionForStage = false;
    state.activeWorkspaceView = "settings";
    $("list-view").hidden = true;
    $("detail-view").hidden = false;
    $("lab-title").textContent = lab.title;
    $("lab-description").textContent = lab.description;
    $("lab-meta").textContent = `${lab.provider} ・ ${lab.difficulty} ・ ${lab.mode}`;
    list($("objectives"), lab.learning_objectives);
    list($("simplifications"), lab.simplifications.map(item => `${item.title}: ${item.description}`));
    ensureLayout();
    state.selectedOptions = {};
    state.controlFields = new Map();
    let supported = true;
    for (const control of lab.controls) {
      if (control.type !== "single_choice") {
        supported = false;
        continue;
      }
      const copy = state.module && state.module.controls[control.id];
      state.controlFields.set(control.id, makeControlField(control, copy));
    }
    if (!supported) setError("このLabには現在未対応のControl形式があります。");
    if (!state.module) setError("このLabの教材定義を読み込めませんでした。自由に設定を試すことはできます。");
    renderTopology(lab, null);
    renderLearning();
    if (!state.module) renderControls();
    setWorkspaceView("settings");
    updateMobileActions();
    updateRun();
  }

  function renderStageResult() {
    const stage = currentStage();
    state.nextStageAvailable = false;
    if (state.mode !== "guided" || !stage || !matchingExperiment(stage)) {
      if (state.mode === "guided" && stage && stage.run_requirement === "all_experiments") {
        state.ranDifferentSelectionForStage = true;
        $("stage-run-note").textContent = "このStageの推奨設定とは異なる設定で実行しました。結果は表示していますが、このStageは完了していません。";
      }
      updateMobileActions();
      return;
    }
    state.ranDifferentSelectionForStage = false;
    const experiment = matchingExperiment(stage);
    if (!experiment) { updateMobileActions(); return; }
    state.completedExperiments.add(experiment.experiment_id);
    if (stageComplete(stage)) state.completed.add(state.stage);
    $("stage-conclusion").textContent = experiment.result_copy;
    list($("stage-observations"), experiment.observation_points);
    list($("stage-misconceptions"), experiment.common_misconceptions);
    list($("stage-exam-points"), experiment.exam_judgment_points);
    $("stage-after").hidden = false;
    $("next-stage").textContent = stage.next_stage_label || "次へ進む";
    $("next-stage").hidden = false;
    state.nextStageAvailable = stageComplete(stage);
    updateMobileActions();
  }

  function renderResult(result) {
    state.runResult = result;
    state.dirty = false;
    const stage = currentStage();
    const experiment = state.mode === "guided" && stage ? matchingExperiment(stage) : null;
    const guided = Boolean(experiment);
    const origin = labelFor(state.currentLab, result.response_origin_node_id);
    $("result-conclusion-title").textContent = guided ? experiment.title : "仮想テストが完了しました";
    $("result-conclusion").textContent = guided ? experiment.result_copy : "応答元と到達地点を確認して、何が起きたかを読み解きましょう。";
    const facts = $("result-facts");
    facts.replaceChildren();
    const addFact = (label, value) => facts.append(text("dt", label), text("dd", value));
    addFact("仮想HTTP status", String(result.http_status));
    addFact("応答元", origin);
    const reached = state.currentLab.topology.nodes.filter(node => result.node_states.find(item => item.node_id === node.id && item.state !== "not_reached")).map(node => node.display.label);
    addFact("到達地点", reached.length ? reached[reached.length - 1] : "到達できませんでした");
    addFact("終了理由", displayFor("termination", result.termination_reason, "仮想テストの処理が終了"));
    if (result.failure_point) addFact("問題が発生した場所", labelFor(state.currentLab, result.failure_point.node_id));
    $("result-path").textContent = "到達経路: " + (reached.length ? reached.join(" → ") : "到達できませんでした");
    $("explanation").textContent = guided ? "サーバーの結果と、上の学習ポイントを合わせて確認してください。" : "サーバーが返した説明: " + result.explanation;
    const applied = $("applied");
    applied.replaceChildren(...state.currentLab.controls.map(control => {
      const copy = state.module && state.module.controls[control.id];
      const option = state.selectedOptions[control.id];
      const label = copy && copy.options[option] ? copy.options[option].label : control.options.find(item => item.id === option)?.label || option;
      return text("li", `${copy ? copy.label : control.label}: ${label}`);
    }));
    const events = $("events");
    events.replaceChildren(...result.events.map(event => text("li", `${displayFor("events", event.kind, "未対応のイベント")} — ${labelFor(state.currentLab, event.node_id)}`)));
    const states = $("states");
    states.replaceChildren(...state.currentLab.topology.nodes.map(node => {
      const value = result.node_states.find(item => item.node_id === node.id)?.state;
      return text("li", `${node.display.label}: ${value ? displayFor("nodeStates", value, "未対応の状態") : "状態が報告されていません"}`);
    }));
    const technical = $("technical-details");
    technical.replaceChildren(
      text("p", "Raw Outcome: " + result.outcome_kind),
      text("p", "Raw Termination Reason: " + result.termination_reason),
      text("p", "Raw response origin ID: " + result.response_origin_node_id)
    );
    if (result.failure_point) technical.append(text("p", `Raw Failure Point: ${result.failure_point.node_id} (${result.failure_point.reason})`));
    const eventDetails = document.createElement("ul");
    eventDetails.append(...result.events.map(event => text("li", `${event.kind} / ${event.node_id} / ${event.at_ms}ms`)));
    technical.append(text("h4", "Raw Event"), eventDetails);
    const stateDetails = document.createElement("ul");
    stateDetails.append(...result.node_states.map(node => text("li", `${node.node_id}: ${node.state}`)));
    technical.append(text("h4", "Raw Node State"), stateDetails);
    renderTopology(state.currentLab, result);
    $("result").hidden = false;
    $("result-empty").hidden = true;
    $("dirty").hidden = true;
    renderStageResult();
    state.activeWorkspaceView = "result";
    resetPaneScroll();
    setWorkspaceView("result");
    if (isDesktop()) $("result").focus();
  }

  async function load() {
    setLoading(true);
    setError("");
    try {
      const parts = location.pathname.split("/").filter(Boolean);
      if (parts.length === 1) renderList(await api("/api/v2/labs"));
      else if (parts.length === 2) {
        const id = encodeURIComponent(parts[1]);
        const [lab, module] = await Promise.all([api("/api/v2/labs/" + id), api("/api/v2/labs/" + id + "/learning")]);
        renderDetail(lab, module);
      }
      else throw new Error("not_found");
    } catch (error) {
      setError(error.message === "lab_not_found" || error.message === "not_found" ? "指定されたLabは見つかりません。" : "Labを読み込めませんでした。もう一度お試しください。");
    } finally {
      setLoading(false);
    }
  }

  function runErrorMessage(code) {
    switch (code) {
      case "invalid_request": return "実行内容を確認して、もう一度お試しください。";
      case "invalid_selection": return "選択内容を確認して、もう一度実行してください。";
      case "lab_not_found": return "指定されたLabは見つかりません。";
      case "internal_error": return "テストを実行できませんでした。時間をおいてもう一度お試しください。";
      case "network_error": return "接続できませんでした。接続を確認して、もう一度お試しください。";
      default: return "テストを実行できませんでした。設定を確認して、もう一度お試しください。";
    }
  }

  function buildRunRequest() {
    const test = state.currentLab && state.currentLab.tests.length === 1 ? state.currentLab.tests[0] : null;
    if (!test) return null;
    const selections = {};
    for (const control of state.currentLab.controls) selections[control.id] = state.selectedOptions[control.id];
    return { test_id: test.id, selections };
  }

  function focusInvalidControl(control) {
    const input = document.querySelector(`input[data-control="${control.id}"]`);
    if (input) input.focus({ preventScroll: false });
  }

  async function runSimulation() {
    const validation = validateSelections();
    if (!validation.valid) {
      const copy = state.module && state.module.controls[validation.control.id];
      const label = copy ? copy.label : validation.control.label;
      setError(`「${label}」が選択されていません。設定を確認して、もう一度実行してください。`);
      state.activeWorkspaceView = "settings";
      setWorkspaceView("settings");
      updateRun();
      focusInvalidControl(validation.control);
      return;
    }
    const request = buildRunRequest();
    if (!request) {
      setError("このLabでは現在テストを実行できません。");
      updateRun();
      return;
    }
    state.running = true;
    setError("");
    setLoading(true);
    updateRun();
    try {
      const response = await fetch("/api/v2/labs/" + encodeURIComponent(state.currentLab.id) + "/runs", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(request)
      });
      const contentType = response.headers.get("content-type") || "";
      const body = contentType.includes("application/json") ? await response.json() : null;
      if (!response.ok) throw new Error(body && body.error && body.error.code || "run_failed");
      renderResult(body);
    } catch (error) {
      const code = error instanceof TypeError ? "network_error" : error.message;
      setError(runErrorMessage(code));
      $("result-empty").hidden = false;
      $("result-empty").textContent = "設定を確認して、もう一度仮想テストを実行してください。";
      state.activeWorkspaceView = "settings";
      setWorkspaceView("settings");
    } finally {
      state.running = false;
      setLoading(false);
      updateRun();
      updateMobileActions();
    }
  }

  $("guided-mode").addEventListener("click", () => {
    if (state.mode !== "guided") resetResult(false);
    state.mode = "guided";
    state.activeWorkspaceView = "settings";
    renderStage();
    updateMode();
  });
  $("free-mode").addEventListener("click", () => {
    if (state.mode !== "free") resetResult(false);
    state.mode = "free";
    state.activeWorkspaceView = "settings";
    updateMode();
  });
  $("return-guided").addEventListener("click", () => {
    resetResult(false);
    state.mode = "guided";
    state.activeWorkspaceView = "settings";
    renderStage();
    updateMode();
    $("stage-title").focus();
  });
  $("apply-stage").addEventListener("click", applyStage);
  $("next-stage").addEventListener("click", goNext);
  window.addEventListener("resize", () => {
    if (!state.currentLab || !$("guided-layout")) return;
    renderControls();
    setWorkspaceView(state.activeWorkspaceView);
    updateMobileActions();
  });
  $("run").addEventListener("click", runSimulation);

  load();
})();
