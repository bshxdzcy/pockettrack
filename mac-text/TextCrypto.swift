import Foundation
import CryptoKit

struct TextCrypto {
    static func key(_ hex: String) -> SymmetricKey? {
        guard hex.count == 64, hex.allSatisfy({ $0.isHexDigit && !$0.isUppercase }) else { return nil }
        var bytes = Data();var index=hex.startIndex
        while index < hex.endIndex { let next=hex.index(index,offsetBy:2);guard let byte=UInt8(hex[index..<next],radix:16) else{return nil};bytes.append(byte);index=next }
        return SymmetricKey(data:bytes)
    }
    static func proof(_ key: SymmetricKey,_ session: Data,_ role: String) -> Data {
        var data=Data("PocketTrack-text-v1:\(role):".utf8);data.append(session)
        return Data(HMAC<SHA256>.authenticationCode(for:data,using:key).prefix(16))
    }
    static func equal(_ a: Data,_ b: Data) -> Bool {guard a.count==b.count else{return false};return zip(a,b).reduce(UInt8(0)){$0 | ($1.0 ^ $1.1)} == 0}
    static func open(_ packet: Data,_ session: Data,_ key: SymmetricKey) throws -> (Data,String) {
        guard session.count==16,packet.count>=45,packet.count<=8236 else {throw Failure.invalid}
        let box=try AES.GCM.SealedBox(combined:packet)
        let plain=try AES.GCM.open(box,using:key,authenticating:session)
        guard plain.count>16,plain.count<=8208,let text=String(data:plain.dropFirst(16),encoding:.utf8),!text.isEmpty,
              !text.unicodeScalars.contains(where:{($0.value<32 && $0.value != 10 && $0.value != 9) || $0.value==127}) else {throw Failure.invalid}
        return (Data(plain.prefix(16)),text)
    }
    enum Failure: Error {case invalid}
}
struct TextFrames {
    private(set) var bytes=Data()
    private var next=0,total=0
    mutating func reset(){bytes.removeAll();next=0;total=0}
    mutating func add(_ frame: Data) throws -> Data? {
        let f=[UInt8](frame)
        guard f.count>=5,f.count<=184 else{throw TextCrypto.Failure.invalid}
        let index=Int(f[0])*256+Int(f[1]),count=Int(f[2])*256+Int(f[3])
        guard count>0,count<=515,index<count else{throw TextCrypto.Failure.invalid}
        if index==0 {reset();total=count}
        guard count==total,index==next else {throw TextCrypto.Failure.invalid}
        bytes.append(contentsOf:f.dropFirst(4));next+=1
        guard bytes.count<=8236 else {throw TextCrypto.Failure.invalid}
        if next==total {let complete=bytes;reset();return complete};return nil
    }
}
