import SwiftUI
import UIKit
@main struct MewApp:App {
    @StateObject var store=Store()
    @AppStorage("theme") var theme="system"
    @Environment(\.scenePhase) var scenePhase
    var body:some Scene { WindowGroup { RootView().modifier(DebugTypeSize()).environmentObject(store).environment(\.layoutDirection,.rightToLeft).environment(\.locale,Locale(identifier:"ar_EG")).environment(\.calendar,Calendar(identifier:.gregorian)).preferredColorScheme(theme=="dark" ? .dark:theme=="light" ? .light:nil).tint(Brand.green).task { await store.load() }.onChange(of:scenePhase){ _,phase in if phase != .active { store.unlocked=false } } } }
}
struct EditorRoute:Identifiable { let id=uid(); var kind:String; var transaction:Transaction?; var due:Due?; var template:QuickTemplate?; var account:Account?; var rule:BillRule?; var category:Category? }
struct RootView:View {
    @EnvironmentObject var store:Store
    @Environment(\.scenePhase) var scenePhase
    @Environment(\.colorScheme) var scheme
    @State var captured=UIScreen.main.isCaptured
    @State var selected="home"
    @State var editor:EditorRoute?
    @State var search=false
    @State var offset=0
    @AppStorage("theme") var theme="system"
    var body:some View {
        ZStack {
            (scheme == .dark ? Color.black:Brand.ivory).ignoresSafeArea()
            if let h=store.data {
                if h.prefs.lock && !store.unlocked { VStack(spacing:20) { Cat(size:100); Text("بياناتك في أمان").font(.title.bold()); Button("فتح Mew"){Task{await store.unlock()}}.buttonStyle(.borderedProminent) } }
                else if !h.prefs.ready { SetupView() }
                else { app(h) }
            } else if let error=store.loadError { VStack(spacing:20){Cat(size:90);Text(error).multilineTextAlignment(.center); Button("إعادة المحاولة"){Task{await store.load()}}; BackupView(period:Household().cycle()) }.padding() }
            else { ProgressView("فتح بياناتك…") }
            if scenePhase != .active || captured { Brand.ivory.ignoresSafeArea(); VStack { Cat(size:90);Text("Mew — بياناتك خاصة").foregroundStyle(Brand.ink) } }
        }
        .sheet(item:$editor){route in if let h=store.data { editorView(route,h).environmentObject(store).environment(\.layoutDirection,.rightToLeft) } }
        .sheet(isPresented:$search){NavigationStack{SearchView(open:{route in search=false;DispatchQueue.main.asyncAfter(deadline:.now()+0.3){editor=route}},navigate:{screen in search=false;if ["home","transactions","analytics","plan","more"].contains(screen){selected=screen}else{DispatchQueue.main.asyncAfter(deadline:.now()+0.3){editor=EditorRoute(kind:screen)}}}).toolbar{ToolbarItem(placement:.cancellationAction){Button("إغلاق"){search=false}}}}}
        .overlay(alignment:.top) { if let message=store.message { VStack { Text(message).font(.subheadline).padding(12).background(.regularMaterial,in:RoundedRectangle(cornerRadius:14)); if store.deleted != nil { Button("تراجع عن الحذف"){Task{await store.undoDelete()}} }; Button("إغلاق الرسالة"){store.message=nil}.font(.caption) }.padding().accessibilityIdentifier("status").task(id:message){try? await Task.sleep(nanoseconds:5_000_000_000);if store.message==message {store.message=nil}} } }
        .onReceive(NotificationCenter.default.publisher(for:UIScreen.capturedDidChangeNotification)){_ in captured=UIScreen.main.isCaptured}
        .onAppear{
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("-mew-dark"){theme="dark"}
            #endif
        }
    }
    func period(_ h:Household)->Cycle { var p=h.cycle(); if offset<0 { for _ in 0..<(-offset){p=h.cycle(Dates.add(p.start,days:-1))} } else if offset>0 { for _ in 0..<offset {p=h.cycle(p.end)} }; return p }
    func app(_ h:Household)->some View {
        TabView(selection:$selected) {
            navigation("home","الرئيسية","house",h:h){HomeView(period:period(h),open:{editor=$0})}
            navigation("transactions","عمليات","list.bullet",h:h){TransactionsView(period:period(h),open:{editor=$0})}
            navigation("analytics","تحليل","chart.bar",h:h){AnalyticsView(period:period(h),open:{editor=$0})}
            navigation("plan","الخطة","calendar",h:h){PlanView(period:period(h),open:{editor=$0})}
            navigation("more","المزيد","ellipsis",h:h){MoreView(period:period(h),open:{editor=$0})}
        }
        .safeAreaInset(edge:.bottom,spacing:0) { HStack(spacing:8) { Menu { Button("إضافة دخل"){editor=EditorRoute(kind:"income")}; Button("تحويل بين الحسابات"){editor=EditorRoute(kind:"transfer")}; Button("القوالب السريعة"){editor=EditorRoute(kind:"templates")} } label:{ Image(systemName:"ellipsis").frame(width:48,height:48).background(.thinMaterial,in:RoundedRectangle(cornerRadius:16)) }.accessibilityLabel("إجراءات إضافية"); Button {editor=EditorRoute(kind:"expense")} label:{ Label("إضافة مصروف",systemImage:"plus").font(.headline).frame(maxWidth:.infinity,minHeight:48) }.buttonStyle(.borderedProminent).accessibilityIdentifier("quick-add") }.padding(.horizontal,12).padding(.vertical,6).background(.regularMaterial) }
    }
    func navigation<Content:View>(_ tag:String,_ title:String,_ icon:String,h:Household,@ViewBuilder content:()->Content)->some View { NavigationStack { content().navigationTitle("Mew").navigationBarTitleDisplayMode(.inline).toolbar { ToolbarItem(placement:.topBarLeading){Cat(size:30)}; ToolbarItem(placement:.topBarTrailing){Button{search=true}label:{Image(systemName:"magnifyingglass")}.accessibilityLabel("بحث").accessibilityIdentifier("search")}; ToolbarItem(placement:.principal){HStack { Button{offset-=1}label:{Image(systemName:"chevron.right")}.accessibilityLabel("الدورة السابقة");Text(String(period(h).start.prefix(7))).font(.subheadline).monospacedDigit();Button{offset+=1}label:{Image(systemName:"chevron.left")}.accessibilityLabel("الدورة التالية") }} }.scrollContentBackground(.hidden) }.tabItem{Label(title,systemImage:icon)}.tag(tag) }
    @ViewBuilder func editorView(_ route:EditorRoute,_ h:Household)->some View {
        switch route.kind {
        case "expense","income","transfer","transaction","refund","due": TransactionEditor(h:h,route:route)
        case "account": AccountEditor(h:h,editing:route.account)
        case "rule": RuleEditor(h:h,editing:route.rule)
        case "category": CategoryEditor(h:h,editing:route.category)
        case "plan": PlanEditor(h:h,period:period(h))
        case "template": TemplateEditor(h:h,editing:route.template)
        case "goal": GoalEditor(h:h)
        case "templates": NavigationStack{TemplatesView(open:{r in editor=nil;DispatchQueue.main.asyncAfter(deadline:.now()+0.3){editor=r}}).toolbar{ToolbarItem(placement:.cancellationAction){Button("إغلاق"){editor=nil}}}}
        case "accounts": NavigationStack{AccountsView(open:{editor=$0})}
        case "budget": NavigationStack{BudgetView(period:period(h))}
        case "dues": NavigationStack{DuesView(open:{editor=$0})}
        case "categories": NavigationStack{CategoriesView(open:{editor=$0})}
        case "settings": NavigationStack{SettingsView(period:period(h),open:{editor=$0})}
        case "backup": NavigationStack{BackupView(period:period(h))}
        default: Text("الشاشة غير متاحة")
        }
    }
}

struct DebugTypeSize:ViewModifier { @Environment(\.dynamicTypeSize) var current; func body(content:Content)->some View {
#if DEBUG
content.environment(\.dynamicTypeSize,ProcessInfo.processInfo.arguments.contains("-mew-large-type") ? .accessibility2:current)
#else
content
#endif
} }
