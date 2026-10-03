import SwiftUI
struct Brand {
    static let green=Color(red:0.14,green:0.45,blue:0.30)
    static let orange=Color(red:0.93,green:0.58,blue:0.32)
    static let ivory=Color(red:1,green:0.973,blue:0.937)
    static let ink=Color(red:0.282,green:0.149,blue:0.137)
    static func accent(_ scheme: ColorScheme) -> Color { scheme == .dark ? Color(red:0.60,green:0.83,blue:0.68):green }
}
struct Cat: View { var size: CGFloat=42; var body: some View { Image("Cat").resizable().scaledToFit().frame(width:size,height:size).accessibilityHidden(true) } }
struct MewCard<Content:View>: View {
    @Environment(\.colorScheme) var scheme
    @Environment(\.accessibilityReduceTransparency) var reduceTransparency
    @AppStorage("glass") var glass=true
    var prominent=false
    @ViewBuilder var content: Content
    var body: some View {
        content.frame(maxWidth:.infinity,alignment:.leading).padding(prominent ? 20:16)
            .background { if glass && !reduceTransparency { RoundedRectangle(cornerRadius:prominent ? 26:18).fill(.ultraThinMaterial).overlay { RoundedRectangle(cornerRadius:prominent ? 26:18).fill(LinearGradient(colors:[Brand.orange.opacity(scheme == .dark ? 0.10:0.06),Brand.green.opacity(0.06)],startPoint:.topTrailing,endPoint:.bottomLeading)) } } else { RoundedRectangle(cornerRadius:prominent ? 26:18).fill(scheme == .dark ? Color(white:0.10):Color.white) } }
            .overlay { RoundedRectangle(cornerRadius:prominent ? 26:18).stroke(.primary.opacity(0.08),lineWidth:1) }
    }
}
struct Page<Content:View>:View { @ViewBuilder var content:Content; var body:some View { ScrollView { LazyVStack(alignment:.leading,spacing:16) { content }.padding(16).frame(maxWidth:650).frame(maxWidth:.infinity) }.scrollDismissesKeyboard(.interactively) } }
struct MoneyView:View { var label:String; var amount:Int64; var main=false; @Environment(\.colorScheme) var scheme; var body:some View { VStack(alignment:.leading,spacing:8) { Text(label).font(main ? .headline:.subheadline).foregroundStyle(.secondary); Text(Finance.format(amount)).font(main ? .largeTitle.bold():.title2.bold()).foregroundStyle(main ? Brand.accent(scheme):.primary).monospacedDigit().lineLimit(1).minimumScaleFactor(0.5).accessibilityLabel(label+" "+Finance.format(amount)) }.frame(maxWidth:.infinity,alignment:.leading) } }
struct Hint:View { let text:String; init(_ text:String){self.text=text}; var body:some View { Text(text).font(.footnote).foregroundStyle(.secondary).fixedSize(horizontal:false,vertical:true) } }
struct EmptyState:View { var title:String; var detail:String; var body:some View { MewCard { VStack(alignment:.leading,spacing:10) { Cat(); Text(title).font(.headline); Hint(detail) } } } }
struct FormField:View { var label:String; @Binding var text:String; var number=false; var identifier:String=""; var body:some View { VStack(alignment:.leading,spacing:6) { Text(label).font(.subheadline); TextField(label,text:$text).keyboardType(number ? .decimalPad:.default).textFieldStyle(.roundedBorder).environment(\.layoutDirection,number ? .leftToRight:.rightToLeft).accessibilityIdentifier(identifier).submitLabel(.next) }.padding(.vertical,2) } }
struct SaveFooter:View { let title:String; let action:()->Void; @EnvironmentObject var store:Store; var body:some View { Button(action:action){ HStack { if store.busy { ProgressView().tint(.white) }; Text(store.busy ? "جارٍ الحفظ…":title).bold().frame(maxWidth:.infinity) }.padding(.vertical,14) }.buttonStyle(.borderedProminent).tint(Brand.green).disabled(store.busy).accessibilityIdentifier("save").padding(12).background(.regularMaterial) } }
struct TxRow:View { var h:Household; var t:Transaction; var body:some View { HStack(alignment:.top,spacing:12) { Image(systemName:t.type == .income ? "arrow.down.circle":t.type == .transfer ? "arrow.left.arrow.right":"creditcard").foregroundStyle(t.type == .income ? Brand.green:Brand.orange).frame(width:28); VStack(alignment:.leading,spacing:5) { Text(t.note.isEmpty ? (t.type == .transfer ? "تحويل بين الحسابات":h.category(t.categoryId)):t.note).font(.headline); Hint(t.date+" • "+h.account(t.accountId)+" • "+t.type.title); Text(Finance.format(t.amount)).bold().monospacedDigit() }; Spacer(minLength:0) }.padding(.vertical,6).frame(maxWidth:.infinity,alignment:.leading) } }
