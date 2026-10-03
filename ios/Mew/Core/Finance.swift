import Foundation

enum Finance {
    static func normalized(_ text: String) -> String { String(text.unicodeScalars.map { s -> Character in switch s.value { case 0x660...0x669: return Character(String(s.value-0x660)); case 0x6f0...0x6f9: return Character(String(s.value-0x6f0)); case 0x66b: return "."; default: return Character(String(s)) } }) }
    static func money(_ text: String) throws -> Int64 {
        let s=normalized(text.trimmingCharacters(in:.whitespacesAndNewlines)); try require(s.range(of:"^[0-9]+(\\.[0-9]{1,2})?$",options:.regularExpression) != nil,"اكتب مبلغًا صحيحًا بخانتين عشريتين كحد أقصى")
        let pieces=s.split(separator:"."); guard let whole=Int64(pieces[0]), whole<=moneyLimit/100 else { throw MewError("المبلغ يتجاوز الحد المسموح") }; let fraction=pieces.count>1 ? Int64(pieces[1])!*(pieces[1].count==1 ? 10:1):0; let value=whole*100+fraction; try require(value<=moneyLimit,"المبلغ يتجاوز الحد المسموح"); return value
    }
    static func input(_ amount: Int64) -> String { String(format:"%lld.%02lld",amount/100,abs(amount%100)) }
    static func format(_ amount: Int64) -> String { let f=NumberFormatter(); f.locale=Locale(identifier:"en_US"); f.numberStyle = .decimal; f.minimumFractionDigits=2; f.maximumFractionDigits=2; return (f.string(from:NSDecimalNumber(value:amount).dividing(by:100)) ?? input(amount))+" ج.م" }
    static func cycle(_ date: String, day: Int) -> Cycle { let current=Dates.anchor(date,day:day); let offset=date<current ? -1:0; return Cycle(start:Dates.anchor(date,day:day,offset:offset),end:Dates.anchor(date,day:day,offset:offset+1)) }
    static func comparison(_ p: Cycle, asOf: String, day: Int) -> [Cycle] { let previous=cycle(Dates.add(p.start,days:-1),day:day); let elapsed=Dates.days(p.start,min(Dates.add(asOf,days:1),p.end)); let days=max(0,min(elapsed,previous.days)); return [Cycle(start:p.start,end:Dates.add(p.start,days:days)),Cycle(start:previous.start,end:Dates.add(previous.start,days:days))] }
    static func balance(_ account: Account, _ entries: [Transaction]) -> Int64 { entries.reduce(account.opening) { total,t in total + (t.accountId==account.id ? ([.income,.refund].contains(t.type) ? t.amount:-t.amount):0) + (t.type == .transfer && t.destinationId==account.id ? t.amount:0) } }
    static func income(_ entries: [Transaction], _ p: Cycle) -> Int64 { entries.filter { p.contains($0.date) && $0.type == .income }.reduce(0) { $0+$1.amount } }
    static func expense(_ entries: [Transaction], _ p: Cycle) -> Int64 { entries.filter { p.contains($0.date) }.reduce(0) { $0 + ($1.type == .expense ? $1.amount:$1.type == .refund ? -$1.amount:0) } }
    static func categories(_ entries: [Transaction], _ p: Cycle) -> [String:Int64] { var r: [String:Int64]=[:]; for t in entries where p.contains(t.date) && [.expense,.refund].contains(t.type) { if let c=t.categoryId { r[c,default:0]+=t.type == .expense ? t.amount:-t.amount } }; return r }
    static func rounded(_ amount: Int64, _ denominator: Int64) -> Int64 { guard denominator>0 else { return 0 }; let q=amount/denominator, r=amount%denominator; return q+(abs(r)*2>=denominator ? (amount<0 ? -1:1):0) }
    static func percent(_ a: Int64, _ b: Int64) -> Double? { b>0 ? Double(a)*100/Double(b):nil }
    static func forecast(_ rows: [Transaction], _ p: Cycle, tracking: String, asOf: String, outstanding: Int64) -> Int64? { let actual=Cycle(start:max(p.start,tracking),end:min(p.end,Dates.add(asOf,days:1))); guard actual.days>=7,p.contains(asOf),rows.contains(where:{ actual.contains($0.date) && $0.type == .expense }) else { return nil }; let variable=max(0,expense(rows.filter { $0.dueId==nil },actual)); let remaining=Dates.days(actual.end,p.end); return expense(rows,actual)+rounded(variable*Int64(remaining),Int64(actual.days))+outstanding }
}
struct CategoryStat: Identifiable { var id: String; var rows: [Transaction]; var gross: Int64 { rows.filter { $0.type == .expense }.reduce(0) { $0+$1.amount } }; var refunds: Int64 { rows.filter { $0.type == .refund }.reduce(0) { $0+$1.amount } }; var net: Int64 { gross-refunds }; var count: Int { rows.filter { $0.type == .expense }.count }; var average: Int64 { Finance.rounded(gross,Int64(count)) } }
struct Signal: Identifiable { var id: String; var title: String; var detail: String; var rows: [Transaction] }
struct Analytics {
    let data: Household; let period: Cycle; var asOf=Dates.today()
    var rows: [Transaction] { data.transactions.filter { period.contains($0.date) && $0.date<=asOf } }
    var income: Int64 { Finance.income(rows,period) }; var expense: Int64 { Finance.expense(rows,period) }; var surplus: Int64 { income-expense }
    var stats: [CategoryStat] { Self.stats(rows) }
    static func stats(_ rows: [Transaction]) -> [CategoryStat] { Dictionary(grouping:rows.filter { [.expense,.refund].contains($0.type) && $0.categoryId != nil },by: { $0.categoryId! }).map { CategoryStat(id:$0.key,rows:$0.value) }.sorted { $0.net>$1.net } }
    var outstanding: Int64 { data.dues.filter { $0.date<period.end }.reduce(0) { $0+data.remaining($1) } }
    var forecast: Int64? { Finance.forecast(data.transactions,period,tracking:data.prefs.trackingStart,asOf:asOf,outstanding:outstanding) }
    var availableDaily: Int64? { let days=Dates.days(Dates.add(asOf,days:1),period.end); guard let b=data.budget(period),period.contains(asOf),days>0 else { return nil }; return max(0,b.amount-expense-outstanding)/Int64(days) }
    var windows: [Cycle] { Finance.comparison(period,asOf:min(asOf,Dates.add(period.end,days:-1)),day:data.prefs.salaryDay) }
    var comparable: Bool { windows[0].days>0 && windows[0].start>=data.prefs.trackingStart && windows[1].start>=data.prefs.trackingStart }
    func buckets(_ unit: String) -> [(String,Int64)] {
        func key(_ date: String) -> String { if unit=="month" { return String(date.prefix(7)) }; if unit=="week",let d=try? Dates.parse(date) { let weekday=Dates.calendar.component(.weekday,from:d); return Dates.add(date,days:-(weekday-1)) }; return date }
        var result: [String:Int64]=[:]; for t in rows where [.expense,.refund].contains(t.type) { result[key(t.date),default:0]+=t.type == .expense ? t.amount:-t.amount }
        var cursor=max(period.start,data.prefs.trackingStart); let end=min(period.end,Dates.add(asOf,days:1)); while cursor<end { if result[key(cursor)]==nil { result[key(cursor)]=0 }; cursor=Dates.add(cursor,days:1) }; return result.sorted { $0.key<$1.key }.map { ($0.key,$0.value) }
    }
    func simulate(_ category: String, reduction: Int) -> (saving:Int64,expense:Int64,surplus:Int64) { let base=max(0,stats.first { $0.id==category }?.net ?? 0); let saving=Finance.rounded(base*Int64(min(50,max(0,reduction))),100); return (saving,expense-saving,surplus+saving) }
    var signals: [Signal] {
        let expenses=rows.filter { $0.type == .expense }; var out=Dictionary(grouping:expenses,by:{ "\($0.date)|\($0.accountId)|\($0.categoryId ?? "")|\($0.amount)" }).filter { $0.value.count>1 }.sorted { $0.key<$1.key }.prefix(3).map { Signal(id:$0.key,title:"عمليات متشابهة تستحق المراجعة",detail:"\($0.value.count) عمليات بنفس اليوم والحساب والصنف والمبلغ. قد تكون مشتريات مستقلة؛ لا حذف تلقائي.",rows:$0.value) }
        var spikes: [(Int64,Signal)]=[]; let ids=Set(expenses.map(\.id))
        for group in Dictionary(grouping:data.transactions.filter { $0.type == .expense && $0.dueId==nil },by:{$0.categoryId}).values {
            var previous: [Transaction]=[]
            for t in group.sorted(by:{ ($0.date,$0.created,$0.id)<($1.date,$1.created,$1.id) }) {
                let before=previous.filter { $0.date<t.date || ($0.date==t.date && $0.created<t.created) }.map(\.amount).sorted()
                if ids.contains(t.id),before.count>=5 { let median=before.count%2==1 ? before[before.count/2]:Finance.rounded(before[before.count/2-1]+before[before.count/2],2); if median>0 && t.amount>=median*3 { spikes.append((t.amount,Signal(id:t.id,title:"مصروف أكبر من نمطك المعتاد",detail:"\(data.category(t.categoryId)): \(Finance.format(t.amount)) مقابل وسيط \(Finance.format(median)). إشارة للمراجعة وليست حكمًا على الضرورة.",rows:[t]))) } }
                previous.append(t); if previous.count>20 { previous.removeFirst() }
            }
        }
        out+=spikes.sorted { $0.0>$1.0 }.prefix(3).map(\.1); return out
    }
    func history(_ count: Int = 6) -> [(Cycle,Int64,Int64)] { var result:[(Cycle,Int64,Int64)]=[]; var p=period; for _ in 0..<count { if p.start>=data.prefs.trackingStart { result.append((p,Finance.income(data.transactions.filter{$0.date<=asOf},p),Finance.expense(data.transactions.filter{$0.date<=asOf},p))) }; p=data.cycle(Dates.add(p.start,days:-1)) }; return result.reversed() }
    static func nextPlan(_ data: Household, asOf: String = Dates.today()) -> (period:Cycle,variable:Int64?,income:Int64?,commitments:Int64,carryover:Int64,history:Int) {
        let current=data.cycle(asOf); let next=data.cycle(current.end); var history: [Cycle]=[]; var p=data.cycle(Dates.add(current.start,days:-1))
        for _ in 0..<3 { if p.start>=data.prefs.trackingStart && data.transactions.contains(where:{ $0.type == .expense && p.contains($0.date) }) { history.append(p) }; p=data.cycle(Dates.add(p.start,days:-1)) }
        let variable=history.isEmpty ? nil:Finance.rounded(history.reduce(0) { $0+max(0,Finance.expense(data.transactions.filter{$0.dueId==nil},$1)) },Int64(history.count))
        let income=history.isEmpty ? nil:Finance.rounded(history.reduce(0) { $0+Finance.income(data.transactions,$1) },Int64(history.count))
        let dues=data.dues.filter{$0.date<next.end && data.remaining($0)>0}; let commitments=dues.filter{next.contains($0.date)}.reduce(0){$0+data.remaining($1)}; let carry=dues.filter{$0.date<next.start}.reduce(0){$0+data.remaining($1)}
        return (next,variable,income,commitments,carry,history.count)
    }
}
