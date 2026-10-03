import Foundation
import CryptoKit
import Security
import CommonCrypto

enum BackupCrypto {
    static let maxSize=32*1024*1024
    static let magic=Data([72,66,66,49])
    static func random(_ count: Int) throws -> Data { var bytes=[UInt8](repeating:0,count:count); try require(SecRandomCopyBytes(kSecRandomDefault,count,&bytes)==errSecSuccess,"تعذر إنشاء مفتاح آمن"); return Data(bytes) }
    static func derive(_ password: String, salt: Data) throws -> SymmetricKey {
        var pass=Array(password.utf8); var bytes=[UInt8](repeating:0,count:32); defer { pass.withUnsafeMutableBytes { $0.initializeMemory(as:UInt8.self,repeating:0) }; bytes.withUnsafeMutableBytes { $0.initializeMemory(as:UInt8.self,repeating:0) } }
        let status=pass.withUnsafeBytes { p in salt.withUnsafeBytes { s in CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2),p.bindMemory(to:Int8.self).baseAddress,pass.count,s.bindMemory(to:UInt8.self).baseAddress,salt.count,CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),210_000,&bytes,32) } }
        try require(status==kCCSuccess,"تعذر تشفير النسخة"); return SymmetricKey(data:Data(bytes))
    }
    static func encrypt(_ plaintext: Data, password: String) throws -> Data {
        try require(password.utf16.count>=8,"كلمة المرور: 8 أحرف على الأقل"); try require(plaintext.count<=maxSize-48,"النسخة تتجاوز 32 ميجابايت")
        let salt=try random(16); let nonce=try AES.GCM.Nonce(data:random(12)); let sealed=try AES.GCM.seal(plaintext,using:derive(password,salt:salt),nonce:nonce,authenticating:magic)
        return magic+salt+Data(nonce)+sealed.ciphertext+sealed.tag
    }
    static func decrypt(_ bytes: Data, password: String) throws -> Data {
        try require(bytes.count>=48 && bytes.count<=maxSize && bytes.prefix(4)==magic,"الملف ليس نسخة Mew سليمة")
        let data=Data(bytes); let key=try derive(password,salt:data.subdata(in:4..<20)); let sealed=try AES.GCM.SealedBox(nonce:AES.GCM.Nonce(data:data.subdata(in:20..<32)),ciphertext:data.subdata(in:32..<(data.count-16)),tag:data.suffix(16)); return try AES.GCM.open(sealed,using:key,authenticating:magic)
    }
    static func encode(_ data: Household, password: String) throws -> Data { try encrypt(JSONEncoder().encode(data.validated()),password:password) }
    static func decode(_ bytes: Data, password: String) throws -> Household { var result=try JSONDecoder().decode(Household.self,from:decrypt(bytes,password:password)).validated(); result.schema=2; return result }
}

enum DeviceKey {
    static func load(create: Bool) throws -> SymmetricKey {
        let query: [String:Any]=[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"Mew.vault.v1",kSecAttrAccount as String:"device-key",kSecReturnData as String:true,kSecMatchLimit as String:kSecMatchLimitOne]
        var value: CFTypeRef?; let status=SecItemCopyMatching(query as CFDictionary,&value)
        if status==errSecSuccess,let bytes=value as? Data,bytes.count==32 { return SymmetricKey(data:bytes) }
        guard status==errSecItemNotFound && create else { throw MewError("تعذر فتح مفتاح البيانات. لم تُحذف البيانات؛ افتح قفل الهاتف أو استعد نسخة سليمة") }
        let bytes=try BackupCrypto.random(32)
        let item: [String:Any]=[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"Mew.vault.v1",kSecAttrAccount as String:"device-key",kSecValueData as String:bytes,kSecAttrAccessible as String:kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        try require(SecItemAdd(item as CFDictionary,nil)==errSecSuccess,"تعذر حفظ مفتاح البيانات"); return SymmetricKey(data:bytes)
    }
}
actor Repository {
    let directory: URL
    let testKey: SymmetricKey?
    init(directory: URL? = nil, key: SymmetricKey? = nil) throws { self.directory=try directory ?? FileManager.default.url(for:.applicationSupportDirectory,in:.userDomainMask,appropriateFor:nil,create:true).appendingPathComponent("Mew",isDirectory:true); testKey=key; try FileManager.default.createDirectory(at:self.directory,withIntermediateDirectories:true); var u=self.directory; var values=URLResourceValues(); values.isExcludedFromBackup=true; try u.setResourceValues(values) }
    var vault: URL { directory.appendingPathComponent("house.vault") }
    func key() throws -> SymmetricKey { if let testKey { return testKey }; return try DeviceKey.load(create:!FileManager.default.fileExists(atPath:vault.path)) }
    func encrypted(_ data: Household) throws -> Data { let clear=try JSONEncoder().encode(data.validated()); try require(clear.count<=BackupCrypto.maxSize-48); return try AES.GCM.seal(clear,using:key()).combined! }
    func decode(_ bytes: Data) throws -> Household { try require(bytes.count<=BackupCrypto.maxSize); var data=try JSONDecoder().decode(Household.self,from:AES.GCM.open(AES.GCM.SealedBox(combined:bytes),using:key())).validated(); data.schema=2; return data }
    func read(_ url: URL) throws -> Data { let size=try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0; try require(size<=BackupCrypto.maxSize); return try Data(contentsOf:url) }
    func load() throws -> Household { if !FileManager.default.fileExists(atPath:vault.path) { return Household() }; return try decode(read(vault)) }
    func write(_ bytes: Data, to url: URL) throws {
        #if os(iOS)
        try bytes.write(to:url,options:[.atomic,.completeFileProtection])
        #else
        try bytes.write(to:url,options:.atomic)
        #endif
        var u=url; var values=URLResourceValues(); values.isExcludedFromBackup=true; try u.setResourceValues(values)
    }
    func save(_ data: Household) throws { try write(encrypted(data),to:vault) }
    func restore(_ incoming: Household, current: Household) throws -> Household { var next=incoming; next.prefs.lock=current.prefs.lock; next=try next.materialized().validated(); try write(encrypted(current),to:directory.appendingPathComponent("pre-restore.vault")); try save(next); return next }
    func undoRestore() throws -> Household { let previous=try decode(read(directory.appendingPathComponent("pre-restore.vault"))).materialized().validated(); try save(previous); return previous }
    func exportBackup(_ h: Household, password: String) throws -> Data { try BackupCrypto.encode(h,password:password) }
    func inspectBackup(_ bytes: Data, password: String) throws -> Household { try BackupCrypto.decode(bytes,password:password) }
}
