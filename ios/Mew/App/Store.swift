import SwiftUI
import LocalAuthentication
import UserNotifications
import UIKit

@MainActor final class Store: ObservableObject {
    @Published var data: Household?
    @Published var busy=false
    @Published var message: String?
    @Published var loadError: String?
    @Published var restorePreview: Household?
    @Published var unlocked=false
    @Published var deleted: Transaction?
    let repository: Repository?
    init() {
        do {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("-mew-ui-test") {
                let url=FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0].appendingPathComponent("Mew-UI-Test")
                if ProcessInfo.processInfo.arguments.contains("-mew-reset") { try? FileManager.default.removeItem(at:url) }
                repository=try Repository(directory:url)
            } else { repository=try Repository() }
            #else
            repository=try Repository()
            #endif
        } catch { repository=nil; loadError="تعذر فتح مخزن البيانات. لم تُحذف البيانات." }
    }
    func load() async {
        guard let repository else { return }; busy=true; defer { busy=false }
        do {
            var h=try await repository.load()
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("-mew-ui-test") && ProcessInfo.processInfo.arguments.contains("-mew-reset") {
                h=Household(); h.accounts=[Account(id:"cash",name:"النقد",opening:200000),Account(id:"bank",name:"البنك")]; h.prefs.ready=true; h.prefs.salaryDay=1; h.prefs.trackingStart="2020-01-01"; h.prefs.defaultAccount="cash"; h.transactions=[Transaction(type:.income,amount:150000,accountId:"cash",categoryId:"salary"),Transaction(type:.expense,amount:35000,accountId:"cash",categoryId:"expense-0")]; try await repository.save(h)
            }
            #endif
            let next=try h.materialized().validated(); if h != next { try await repository.save(next) }; data=next; loadError=nil
            await Reminders.schedule(next)
        } catch { loadError="تعذر فتح البيانات. لم تُحذف أو تُستبدل. أعد المحاولة أو استعد نسخة سليمة." }
    }
    @discardableResult func change(_ success: String = "تم الحفظ", transform: (inout Household) throws -> Void) async -> Bool {
        guard !busy, var next=data, let repository else { return false }; busy=true; defer { busy=false }
        do { try transform(&next); next=try next.validated().materialized().validated(); try await repository.save(next); data=next; message=success; await Reminders.schedule(next); return true }
        catch { message=(error as? MewError)?.message ?? "تعذر الحفظ. لم تتغير البيانات؛ راجع مساحة الهاتف وأعد المحاولة."; return false }
    }
    func save(_ t: Transaction, fee: Int64 = 0, feeId: String = uid(), template: QuickTemplate? = nil) async -> Bool {
        await change("تم حفظ العملية") { h in h.transactions.removeAll { $0.id==t.id }; h.transactions.append(t); if fee>0 && t.type == .transfer { h.transactions.removeAll{$0.id==feeId}; h.transactions.append(Transaction(id:feeId,type:.expense,amount:fee,date:t.date,accountId:t.accountId,categoryId:"expense-8",payment:t.payment,note:"رسوم تحويل \(t.id)")) }; if let template { h.templates.removeAll{$0.id==template.id}; h.templates.append(template) }; h.schema=2 }
    }
    func delete(_ t: Transaction) async { let ok=await change("حُذفت العملية؛ يمكنك التراجع") { h in try require(!h.transactions.contains{$0.originalId==t.id},"احذف الاستردادات المرتبطة أولًا أو عدّل المصروف"); h.transactions.removeAll{$0.id==t.id} }; if ok { deleted=t } }
    func undoDelete() async { guard let t=deleted else { return }; if await change("تم التراجع",transform:{ h in try require(!h.transactions.contains{$0.id==t.id}); h.transactions.append(t) }) { deleted=nil } }
    func inspect(_ bytes: Data, password: String) async { guard !busy,let repository else{return}; busy=true; defer { busy=false }; do { restorePreview=try await repository.inspectBackup(bytes,password:password) } catch { message="كلمة المرور غير صحيحة أو النسخة تالفة أو غير متوافقة. لم تُستعد البيانات." } }
    func restore() async { guard !busy,let incoming=restorePreview,let current=data,let repository else{return}; busy=true; defer{busy=false}; do { data=try await repository.restore(incoming,current:current); restorePreview=nil; message="تمت الاستعادة دون دمج، مع حفظ نسخة أمان للرجوع"; await Reminders.schedule(data!) } catch { message="تعذرت الاستعادة. لم تُستبدل البيانات الحالية." } }
    func undoRestore() async { guard !busy,let repository else{return}; busy=true; defer{busy=false}; do { data=try await repository.undoRestore(); message="تم الرجوع إلى البيانات السابقة"; await Reminders.schedule(data!) } catch { message="لا توجد نسخة أمان قابلة للاسترجاع" } }
    func unlock() async { do { let context=LAContext(); unlocked=try await context.evaluatePolicy(.deviceOwnerAuthentication,localizedReason:"فتح Mew باستخدام Face ID أو قفل الهاتف") } catch { message="لم يتم فتح القفل؛ أعد المحاولة" } }
    func canLock() -> Bool { LAContext().canEvaluatePolicy(.deviceOwnerAuthentication,error:nil) }
}
enum Reminders {
    static func enable(_ h: Household?) async -> String { do { let allowed=try await UNUserNotificationCenter.current().requestAuthorization(options:[.alert,.sound,.badge]); if allowed { if let h { await schedule(h) }; return "تم تفعيل التذكير؛ لا تظهر تفاصيل مالية" }; return "التنبيهات غير مفعلة؛ الالتزامات متاحة داخل التطبيق" } catch { return "تعذر تفعيل التنبيهات؛ راجع إعدادات iPhone" } }
    static func schedule(_ h: Household) async {
        let center=UNUserNotificationCenter.current(); let settings=await center.notificationSettings(); guard [.authorized,.provisional].contains(settings.authorizationStatus) else{return}
        let old=await center.pendingNotificationRequests(); center.removePendingNotificationRequests(withIdentifiers:old.filter{$0.identifier.hasPrefix("mew-due-")}.map(\.identifier))
        let pending=h.dues.filter{h.remaining($0)>0}; guard !pending.isEmpty else { return }; let today=Dates.today()
        // One generic review per day, up to 60 pending notifications (iOS system limit).
        for offset in 0..<60 {
            let date=Dates.add(today,days:offset); guard pending.contains(where:{$0.date<=Dates.add(date,days:3)}) else{continue}; let parts=date.split(separator:"-").compactMap{Int($0)}; var components=DateComponents(); components.calendar=Calendar(identifier:.gregorian); components.year=parts[0]; components.month=parts[1]; components.day=parts[2]; components.hour=9
            if let scheduled=Calendar.current.date(from:components),scheduled<=Date(){continue}
            let content=UNMutableNotificationContent(); content.title="Mew"; content.body="لديك التزامات قريبة أو متأخرة؛ افتح التطبيق للمراجعة"; content.sound = .default; content.userInfo=["screen":"dues"]
            try? await center.add(UNNotificationRequest(identifier:"mew-due-"+date,content:content,trigger:UNCalendarNotificationTrigger(dateMatching:components,repeats:false)))
        }
    }
}
enum PDFReport {
    static func make(_ h: Household, _ p: Cycle) -> Data {
        let lines=Reports.lines(h,p); let renderer=UIGraphicsPDFRenderer(bounds:CGRect(x:0,y:0,width:595,height:842)); let paragraph=NSMutableParagraphStyle(); paragraph.alignment = .right; paragraph.baseWritingDirection = .rightToLeft; paragraph.lineSpacing=4
        let attrs:[NSAttributedString.Key:Any]=[.font:UIFont.systemFont(ofSize:13),.foregroundColor:UIColor(red:72/255,green:38/255,blue:35/255,alpha:1),.paragraphStyle:paragraph]
        return renderer.pdfData { context in context.beginPage(); var y:CGFloat=36; var number=1
            func footer(){ let s="\(number)" as NSString; s.draw(at:CGPoint(x:290,y:814),withAttributes:attrs) }
            for line in lines { let text=line as NSString; let rect=text.boundingRect(with:CGSize(width:523,height:CGFloat.greatestFiniteMagnitude),options:[.usesLineFragmentOrigin,.usesFontLeading],attributes:attrs,context:nil); if y+rect.height>795 { footer(); context.beginPage(); number+=1;y=36 }; text.draw(in:CGRect(x:36,y:y,width:523,height:ceil(rect.height)),withAttributes:attrs); y+=ceil(rect.height)+10 }; footer()
        }
    }
}
