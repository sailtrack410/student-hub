import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smartcampus_app/api/api_client.dart';
import 'package:smartcampus_app/api/auth_service.dart';
import 'package:smartcampus_app/services/ai_chat_gateway.dart';
import 'package:smartcampus_app/pages/login/login_page.dart';
import 'package:smartcampus_app/pages/login/register_page.dart';
import 'package:smartcampus_app/pages/login/forgot_password_page.dart';
import 'package:smartcampus_app/pages/assistant/ai_assistant_page.dart';
import 'package:smartcampus_app/pages/service/service_hall_page.dart';
import 'package:smartcampus_app/pages/service/my_requests_page.dart';
import 'package:smartcampus_app/pages/profile/settings_security_page.dart';
import 'package:smartcampus_app/pages/message/notification_center_page.dart';
import 'package:smartcampus_app/models/service_catalog_models.dart';
import 'package:smartcampus_app/theme/app_theme.dart';
import 'package:smartcampus_app/widgets/skeleton_shimmer.dart';

class QuietGateway extends AiChatGateway {
  const QuietGateway();
  @override
  Future<List<Map<String,String>>> fetchRecommendations() async => [];
}
void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));
  final auth = AuthService(ApiClient());
  final pages = <String, Widget Function()>{
    'login': () => LoginPage(authService: auth),
    'register': () => RegisterPage(authService: auth),
    'forgot': () => ForgotPasswordPage(authService: auth),
    'AI': () => const AIAssistantPage(gateway: QuietGateway()),
    'services': () => const ServiceHallPage(initialCategories: [ServiceCategoryData(categoryId:'1',categoryCode:'REPAIR',name:'后勤报修服务',description:'宿舍与公共场所服务',items:[ServiceItemData(itemId:'1',itemCode:'REPAIR-DORM',name:'宿舍及公共区域设施故障报修',summary:'请说明故障位置与联系方式，以便工作人员处理')])]),
    'requests': () => const MyRequestsPage(),
    'messages': () => const NotificationCenterPage(initialItems: []),
    'settings': () => SettingsSecurityPage(authService: auth),
  };
  for (final width in [360.0,390.0,430.0]) {
    for (final scale in [1.3,1.5]) {
      for (final entry in pages.entries) {
        testWidgets('${entry.key} ${width.toInt()}dp $scale text', (tester) async {
          tester.view.devicePixelRatio=1;
          tester.view.physicalSize=Size(width,800);
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          await tester.pumpWidget(MaterialApp(theme: AppTheme.lightTheme,home: MediaQuery(data: MediaQueryData(size: Size(width,800),textScaler:TextScaler.linear(scale),disableAnimations:true,padding:const EdgeInsets.only(bottom:34)),child:entry.value())));
          await tester.pump();
          await tester.pump(const Duration(seconds:1));
          expect(tester.takeException(),isNull);
          final scroll = find.byType(Scrollable).hitTestable();
          if(scroll.evaluate().isNotEmpty) {
            for(var i=0;i<4;i++) {
              await tester.drag(scroll.first,const Offset(0,-420));
              await tester.pump(const Duration(milliseconds:500));
              expect(tester.takeException(),isNull);
            }
          }
          await tester.pumpWidget(const SizedBox());
        });
      }
    }
  }
  testWidgets('login remains scrollable with a short screen and keyboard', (tester) async {
    tester.view.devicePixelRatio=1;
    tester.view.physicalSize=const Size(360,640);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(MaterialApp(theme:AppTheme.lightTheme,home:MediaQuery(data:const MediaQueryData(size:Size(360,640),viewInsets:EdgeInsets.only(bottom:300),textScaler:TextScaler.linear(1.5)),child:LoginPage(authService:auth))));
    await tester.pumpAndSettle();
    expect(tester.takeException(),isNull);
    await tester.ensureVisible(find.text('QQ 一键登录'));
    await tester.tap(find.text('QQ 一键登录'));
    await tester.pumpAndSettle();
    expect(find.textContaining('请先阅读并同意'),findsOneWidget);
    expect(tester.takeException(),isNull);
  });
  testWidgets('reduced motion stops shimmer ticker', (tester) async {
    await tester.pumpWidget(const MaterialApp(home:MediaQuery(data:MediaQueryData(disableAnimations:true),child:SkeletonShimmer(child:Text('loading')))));
    await tester.pumpAndSettle();
    expect(tester.binding.transientCallbackCount,0);
  });
}
