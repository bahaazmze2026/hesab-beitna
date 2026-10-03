import Foundation

let moneyLimit: Int64 = 100_000_000_000
struct MewError: LocalizedError { let message: String; var errorDescription: String? { message }; init(_ message: String) { self.message = message } }
func require(_ condition: Bool, _ message: String = "راجع البيانات والحسابات والتصنيفات والتاريخ") throws { if !condition { throw MewError(message) } }
func uid() -> String { UUID().uuidString }

enum Dates {
    static var calendar: Calendar { var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(secondsFromGMT: 0)!; return c }
    static func parse(_ value: String) throws -> Date {
        let pieces = value.split(separator: "-", omittingEmptySubsequences: false)
        guard pieces.count == 3, pieces[0].count == 4, pieces[1].count == 2, pieces[2].count == 2,
              let y = Int(pieces[0]), let m = Int(pieces[1]), let d = Int(pieces[2]), y >= 1, y <= 9999,
              let date = calendar.date(from: DateComponents(year: y, month: m, day: d)), string(date) == value else { throw MewError("التاريخ غير صالح؛ استخدم YYYY-MM-DD") }
        return date
    }
    static func string(_ date: Date) -> String { let p = calendar.dateComponents([.year,.month,.day], from: date); return String(format: "%04d-%02d-%02d", p.year!, p.month!, p.day!) }
    static func today() -> String { var c = Calendar(identifier: .gregorian); c.timeZone = .current; let p = c.dateComponents([.year,.month,.day], from: Date()); return String(format:"%04d-%02d-%02d",p.year!,p.month!,p.day!) }
    static func add(_ value: String, days: Int = 0, months: Int = 0) -> String { guard let d = try? parse(value), let shifted = calendar.date(byAdding: .month, value: months, to: d), let result = calendar.date(byAdding: .day, value: days, to: shifted) else { return value }; return string(result) }
    static func days(_ start: String, _ end: String) -> Int { guard let s = try? parse(start), let e = try? parse(end) else { return 0 }; return calendar.dateComponents([.day], from: s, to: e).day ?? 0 }
    static func anchor(_ date: String, day: Int, offset: Int = 0) -> String { let d = (try? parse(String(date.prefix(7))+"-01")) ?? Date(); let m = calendar.date(byAdding:.month,value:offset,to:d)!; let length = calendar.range(of:.day,in:.month,for:m)!.count; return string(calendar.date(bySetting:.day,value:min(day,length),of:m)!) }
}
struct Cycle: Equatable, Codable { var start: String; var end: String; func contains(_ date: String) -> Bool { date >= start && date < end }; var days: Int { Dates.days(start,end) } }
enum TxType: String, Codable, CaseIterable, Identifiable { case expense = "EXPENSE", income = "INCOME", transfer = "TRANSFER", refund = "REFUND"; var id: String { rawValue }; var title: String { switch self { case .expense: return "مصروف"; case .income: return "دخل"; case .transfer: return "تحويل"; case .refund: return "استرداد" } } }
struct Account: Codable, Equatable, Identifiable { var id = uid(); var name: String; var kind = "نقد"; var opening: Int64 = 0; var archived = false }
struct Category: Codable, Equatable, Identifiable { var id = uid(); var name: String; var income = false; var archived = false }
struct Transaction: Codable, Equatable, Identifiable { var id = uid(); var type: TxType; var amount: Int64; var date = Dates.today(); var accountId: String; var destinationId: String?; var categoryId: String?; var payment = "نقد"; var note = ""; var originalId: String?; var dueId: String?; var created = Int64(Date().timeIntervalSince1970 * 1000) }
struct Budget: Codable, Equatable, Identifiable { var period: String; var amount: Int64; var categoryId: String?; var id: String { period + ":" + (categoryId ?? "total") } }
struct BillRule: Codable, Equatable, Identifiable { var id = uid(); var title: String; var amount: Int64; var categoryId: String; var start: String; var intervalMonths = 1; var end: String?; var active = true }
struct Due: Codable, Equatable, Identifiable { var id: String; var ruleId: String; var title: String; var categoryId: String; var date: String; var amount: Int64 }
struct SavingGoal: Codable, Equatable { var title = ""; var target: Int64 = 0; var accountId: String? }
struct Preferences: Codable, Equatable { var ready = false; var salaryDay = 25; var trackingStart = Dates.today(); var defaultAccount: String?; var lock = false; var goal = SavingGoal() }
struct MonthlyPlan: Codable, Equatable, Identifiable { var period: String; var income: Int64; var variable: Int64; var commitments: Int64; var saving: Int64; var reserve: Int64; var incomeDate: String; var note = ""; var id: String { period }; var spending: Int64 { variable + commitments }; var allocated: Int64 { spending + saving + reserve } }
struct QuickTemplate: Codable, Equatable, Identifiable { var id = uid(); var title: String; var type: TxType = .expense; var amount: Int64; var accountId: String; var categoryId: String; var note = ""; func draft() -> Transaction { Transaction(type:type,amount:amount,accountId:accountId,categoryId:categoryId,note:note) } }
struct Household: Codable, Equatable {
    var schema = 2; var accounts: [Account] = []; var categories = Household.defaults(); var transactions: [Transaction] = []; var budgets: [Budget] = []; var rules: [BillRule] = []; var dues: [Due] = []; var prefs = Preferences(); var plans: [MonthlyPlan] = []; var templates: [QuickTemplate] = []
    init() {}
    enum CodingKeys: String, CodingKey { case schema,accounts,categories,transactions,budgets,rules,dues,prefs,plans,templates }
    init(from decoder: Decoder) throws { let c = try decoder.container(keyedBy:CodingKeys.self); schema = try c.decode(Int.self,forKey:.schema); accounts = try c.decode([Account].self,forKey:.accounts); categories = try c.decode([Category].self,forKey:.categories); transactions = try c.decode([Transaction].self,forKey:.transactions); budgets = try c.decode([Budget].self,forKey:.budgets); rules = try c.decode([BillRule].self,forKey:.rules); dues = try c.decode([Due].self,forKey:.dues); prefs = try c.decode(Preferences.self,forKey:.prefs); plans = try c.decodeIfPresent([MonthlyPlan].self,forKey:.plans) ?? []; templates = try c.decodeIfPresent([QuickTemplate].self,forKey:.templates) ?? [] }
    static func defaults() -> [Category] { ["الطعام","الفواتير","الإيجار","التعليم","الصحة","المواصلات","الترفيه","الأقساط","رسوم التحويل","أخرى"].enumerated().map { Category(id:"expense-\($0.offset)",name:$0.element) } + [Category(id:"salary",name:"الراتب",income:true),Category(id:"other-income",name:"دخل إضافي",income:true)] }
    func category(_ id: String?) -> String { categories.first { $0.id == id }?.name ?? "—" }
    func account(_ id: String?) -> String { accounts.first { $0.id == id }?.name ?? "—" }
    func cycle(_ date: String = Dates.today()) -> Cycle { Finance.cycle(date, day:prefs.salaryDay) }
    func budget(_ p: Cycle, category: String? = nil) -> Budget? { budgets.first { $0.period == p.start && $0.categoryId == category } }
    func balance(_ account: Account) -> Int64 { Finance.balance(account, transactions) }
    func paid(_ due: Due) -> Int64 { transactions.filter { $0.dueId == due.id }.reduce(0) { $0 + ($1.type == .expense ? $1.amount : $1.type == .refund ? -$1.amount : 0) } }
    func remaining(_ due: Due) -> Int64 { max(0,due.amount-paid(due)) }
    func templateAvailable(_ t: QuickTemplate) -> Bool { accounts.contains { $0.id == t.accountId && !$0.archived } && categories.contains { $0.id == t.categoryId && !$0.archived } }
    func validated(asOf: String = Dates.today()) throws -> Household {
        try require((1...2).contains(schema),"إصدار النسخة غير مدعوم")
        try require(accounts.count<=200 && categories.count<=500 && transactions.count<=100_000 && rules.count<=1000 && dues.count<=50_000 && budgets.count<=20_000 && plans.count<=2400 && templates.count<=100,"الملف أكبر من الحدود المدعومة")
        try require(schema == 2 || (plans.isEmpty && templates.isEmpty))
        try require((1...31).contains(prefs.salaryDay)); _ = try Dates.parse(prefs.trackingStart)
        for ids in [accounts.map(\.id),categories.map(\.id),transactions.map(\.id),rules.map(\.id),dues.map(\.id),budgets.map(\.id),plans.map(\.period),templates.map(\.id)] { try require(Set(ids).count == ids.count,"معرفات أو خطط مكررة") }
        let a = Set(accounts.map(\.id)); let c = Dictionary(uniqueKeysWithValues:categories.map { ($0.id,$0) }); let t = Dictionary(uniqueKeysWithValues:transactions.map { ($0.id,$0) }); let d = Dictionary(uniqueKeysWithValues:dues.map { ($0.id,$0) }); let r = Set(rules.map(\.id))
        for v in accounts { try require(!v.name.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty && (0...moneyLimit).contains(v.opening)) }
        for v in categories { try require(!v.name.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty) }
        if prefs.ready { try require(accounts.contains { !$0.archived } && categories.contains { !$0.archived && !$0.income } && categories.contains { !$0.archived && $0.income }) }
        try require(prefs.defaultAccount == nil || accounts.contains { $0.id == prefs.defaultAccount && !$0.archived }); try require((0...moneyLimit).contains(prefs.goal.target) && (prefs.goal.accountId == nil || a.contains(prefs.goal.accountId!)))
        for b in budgets { _ = try Dates.parse(b.period); try require((0...moneyLimit).contains(b.amount) && (b.categoryId == nil || c[b.categoryId!]?.income == false)) }
        for v in rules { _ = try Dates.parse(v.start); if let end=v.end { _=try Dates.parse(end); try require(end>=v.start) }; try require(!v.title.isEmpty && (1...moneyLimit).contains(v.amount) && (1...12).contains(v.intervalMonths) && c[v.categoryId]?.income == false) }
        for v in dues { _=try Dates.parse(v.date); try require((1...moneyLimit).contains(v.amount) && r.contains(v.ruleId) && c[v.categoryId]?.income == false) }
        var refunds: [String:Int64] = [:]
        for v in transactions {
            _=try Dates.parse(v.date); try require(v.date>=prefs.trackingStart && v.date<=asOf,"التاريخ خارج المتابعة أو في المستقبل؛ استخدم الالتزامات")
            try require((1...moneyLimit).contains(v.amount) && a.contains(v.accountId))
            if v.type == .transfer { try require(v.destinationId != nil && a.contains(v.destinationId!) && v.destinationId != v.accountId && v.categoryId == nil && v.originalId == nil && v.dueId == nil) }
            else { try require(v.destinationId == nil && v.categoryId != nil && c[v.categoryId!]?.income == (v.type == .income)) }
            if v.type == .refund { guard let original=v.originalId.flatMap({t[$0]}) else { throw MewError("الاسترداد يحتاج المصروف الأصلي") }; try require(original.type == .expense && original.categoryId == v.categoryId && original.dueId == v.dueId && v.date>=original.date); refunds[original.id,default:0]+=v.amount }
            else { try require(v.originalId == nil) }
            if let id=v.dueId { try require(d[id]?.categoryId == v.categoryId && (v.type == .expense || v.type == .refund)) }
        }
        for v in transactions where v.type == .expense { try require(refunds[v.id,default:0]<=v.amount,"الاستردادات تتجاوز المصروف الأصلي") }
        for v in dues { try require((0...v.amount).contains(paid(v)),"السداد يتجاوز الالتزام") }
        for p in plans { _=try Dates.parse(p.period); _=try Dates.parse(p.incomeDate); try require(p.incomeDate>=p.period && p.incomeDate<Dates.add(p.period,days:4,months:1)); try require([p.income,p.variable,p.commitments,p.saving,p.reserve].allSatisfy { (0...moneyLimit).contains($0) } && p.note.count<=500) }
        for v in templates { try require(!v.title.isEmpty && v.title.count<=60 && v.note.count<=500 && [.expense,.income].contains(v.type) && (1...moneyLimit).contains(v.amount) && a.contains(v.accountId) && c[v.categoryId]?.income == (v.type == .income)) }
        return self
    }
    func materialized(asOf: String = Dates.today()) -> Household {
        var result=self; var ids=Set(dues.map(\.id)); let horizon=Dates.add(asOf,months:13)
        for rule in rules where rule.active {
            let first=String(rule.start.prefix(7))+"-01"; let elapsed=max(0,(try? Dates.calendar.dateComponents([.month],from:Dates.parse(first),to:Dates.parse(String(prefs.trackingStart.prefix(7))+"-01")).month) ?? 0)
            var offset=elapsed/rule.intervalMonths*rule.intervalMonths
            for _ in 0..<2400 {
                let date=Dates.anchor(first,day:Int(rule.start.suffix(2)) ?? 1,offset:offset)
                if date>horizon || (rule.end != nil && date>rule.end!) { break }
                let id=rule.id+":"+date
                if date>=prefs.trackingStart && !ids.contains(id) { result.dues.append(Due(id:id,ruleId:rule.id,title:rule.title,categoryId:rule.categoryId,date:date,amount:rule.amount)); ids.insert(id) }
                offset+=rule.intervalMonths
            }
        }
        result.dues.sort { $0.date<$1.date }; return result
    }
}
