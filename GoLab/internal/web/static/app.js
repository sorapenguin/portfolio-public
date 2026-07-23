'use strict';

var currentScenario = null;
var selectedChoiceId = null;

function showList() {
  document.getElementById('view-list').classList.remove('hidden');
  document.getElementById('view-scenario').classList.add('hidden');
  currentScenario = null;
  selectedChoiceId = null;
}

function showScenario(id) {
  fetch('/api/scenarios/' + id)
    .then(function(r) { return r.json(); })
    .then(function(sc) {
      currentScenario = sc;
      selectedChoiceId = null;
      renderScenario(sc);
      document.getElementById('view-list').classList.add('hidden');
      document.getElementById('view-scenario').classList.remove('hidden');
      document.getElementById('result-section').classList.add('hidden');
      document.getElementById('run-btn').disabled = true;
      window.scrollTo(0, 0);
    });
}

function renderScenario(sc) {
  document.getElementById('sc-title').textContent = sc.title;
  document.getElementById('sc-description').textContent = sc.description;
  document.getElementById('sc-difficulty').textContent = sc.difficulty;
  document.getElementById('sc-category').textContent = sc.category;

  var goals = document.getElementById('sc-goals');
  goals.innerHTML = '';
  (sc.learningGoals || []).forEach(function(g) {
    var li = document.createElement('li');
    li.textContent = g;
    goals.appendChild(li);
  });

  var nodePath = document.getElementById('sc-nodes');
  nodePath.innerHTML = '';
  (sc.nodes || []).forEach(function(n, i) {
    if (i > 0) {
      var arrow = document.createElement('span');
      arrow.className = 'node-arrow';
      arrow.textContent = '→';
      nodePath.appendChild(arrow);
    }
    var chip = document.createElement('span');
    chip.className = 'node-chip';
    chip.textContent = n.label;
    nodePath.appendChild(chip);
  });

  var choicesEl = document.getElementById('sc-choices');
  choicesEl.innerHTML = '';
  (sc.choices || []).forEach(function(c) {
    var btn = document.createElement('button');
    btn.className = 'choice-btn';
    btn.textContent = c.label;
    btn.dataset.id = c.id;
    btn.onclick = function() { selectChoice(c.id); };
    choicesEl.appendChild(btn);
  });
}

function selectChoice(id) {
  selectedChoiceId = id;
  document.querySelectorAll('.choice-btn').forEach(function(b) {
    b.classList.toggle('selected', b.dataset.id === id);
  });
  document.getElementById('run-btn').disabled = false;
}

function runSimulation() {
  if (!currentScenario || !selectedChoiceId) return;

  document.getElementById('run-btn').disabled = true;

  fetch('/api/simulate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ scenarioId: currentScenario.id, choiceId: selectedChoiceId })
  })
    .then(function(r) { return r.json(); })
    .then(function(result) {
      renderResult(result);
      document.getElementById('run-btn').disabled = false;
    });
}

function renderResult(result) {
  var verdict = document.getElementById('result-verdict');
  verdict.textContent = result.correct ? '正解！' : '不正解';
  verdict.className = 'verdict ' + (result.correct ? 'correct' : 'wrong');

  var timeline = document.getElementById('result-timeline');
  timeline.innerHTML = '';
  (result.events || []).forEach(function(e) {
    var li = document.createElement('li');
    var delay = document.createElement('span');
    delay.className = 'tl-delay';
    delay.textContent = e.delayMs + ' ms';
    var status = document.createElement('span');
    status.className = 'tl-status ' + e.status;
    status.textContent = e.status;
    var desc = document.createElement('span');
    desc.className = 'tl-desc';
    desc.textContent = (e.nodeLabel || e.nodeId) + ': ' + e.description;
    li.appendChild(delay);
    li.appendChild(status);
    li.appendChild(desc);
    timeline.appendChild(li);
  });

  document.getElementById('result-explanation').textContent = result.explanation || '';

  var cloudBlock = document.getElementById('cloud-mapping');
  var cloudTable = document.getElementById('cloud-table');
  cloudTable.innerHTML = '';
  if (currentScenario && currentScenario.cloudMapping && Object.keys(currentScenario.cloudMapping).length > 0) {
    var header = '<tr><th>ノード</th><th>クラウドの例</th></tr>';
    var rows = Object.keys(currentScenario.cloudMapping).map(function(k) {
      return '<tr><td>' + k + '</td><td>' + currentScenario.cloudMapping[k] + '</td></tr>';
    }).join('');
    cloudTable.innerHTML = header + rows;
    cloudBlock.classList.remove('hidden');
  } else {
    cloudBlock.classList.add('hidden');
  }

  document.getElementById('result-section').classList.remove('hidden');
  document.getElementById('result-section').scrollIntoView({ behavior: 'smooth' });
}

function loadScenarioList() {
  fetch('/api/scenarios')
    .then(function(r) { return r.json(); })
    .then(function(list) {
      var container = document.getElementById('scenario-list');
      container.innerHTML = '';
      if (!list || list.length === 0) {
        container.innerHTML = '<p class="loading">シナリオがありません。</p>';
        return;
      }
      list.forEach(function(sc) {
        var card = document.createElement('div');
        card.className = 'scenario-card';
        card.innerHTML =
          '<h3>' + sc.title + '</h3>' +
          '<p>' + sc.description + '</p>' +
          '<div class="meta-row">' +
            '<span class="badge">' + sc.difficulty + '</span>' +
            '<span class="badge badge-category">' + sc.category + '</span>' +
          '</div>';
        card.onclick = function() { showScenario(sc.id); };
        container.appendChild(card);
      });
    })
    .catch(function() {
      document.getElementById('scenario-list').innerHTML =
        '<p class="loading">読み込みに失敗しました。</p>';
    });
}

loadScenarioList();
