from django.urls import path
from django.http import HttpResponse
from . import views

urlpatterns = [
    path("health/", lambda _: HttpResponse("ok"), name="health"),
    path("favicon.ico", lambda _: HttpResponse(status=204)),
    path("favicon.ico/", lambda _: HttpResponse(status=204)),
    path("", views.index),
    path("top/", views.top_page, name="top"),
    path("login/", views.login_view),
    path("logout/", views.logout_view),
    path("register/", views.register_view),
    path("register/verify/", views.register_verify_view),
    path("start/", views.start_quiz_page),
    path("stamina/recover/", views.recover_stamina_view),
    path("gem/use/", views.use_gem_view),
    path("mini-gem/claim/", views.claim_mini_gems_view),
    path("mini-gem/exchange/", views.exchange_mini_gems_view),
    path("purchase/confirm/", views.purchase_confirm_view),
    path("question/<int:q_id>/", views.question_page),
    path("answer/", views.answer_page),
    path("result/", views.result_page),
    path("history/", views.history_page),
    path("mypage/", views.mypage_view),
    # 管理パネル（難読化URL）
    path("hidden-admin-HUE-node/login/", views.admin_login_view),
    path("hidden-admin-HUE-node/logout/", views.admin_logout_view),
    path("hidden-admin-HUE-node/", views.admin_dashboard),
    path("hidden-admin-HUE-node/questions/", views.admin_question_list),
    path("hidden-admin-HUE-node/questions/<int:q_id>/edit/", views.admin_question_edit),
    path("hidden-admin-HUE-node/questions/<int:q_id>/delete/", views.admin_question_delete),
    path("hidden-admin-HUE-node/questions/<str:category>/", views.admin_question_list),
    # カテゴリ別ページ（末尾に置き特定パスと競合させない）
    path("<str:category>/history/", views.category_history),
    path("<str:category>/start/", views.category_start),
    path("<str:category>/", views.category_index),
]