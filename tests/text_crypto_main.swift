import Foundation
import CryptoKit
let rows=try String(contentsOfFile:CommandLine.arguments[1],encoding:.utf8).split(separator:"\n")
let packet=Data(base64Encoded:String(rows[0]))!,key=SymmetricKey(data:Data((0..<32).map{UInt8($0)})),session=Data((32..<48).map{UInt8($0)})
precondition(TextCrypto.proof(key,session,"mac")==Data(base64Encoded:String(rows[1]))!)
precondition(TextCrypto.proof(key,session,"phone")==Data(base64Encoded:String(rows[2]))!)
let (id,text)=try TextCrypto.open(packet,session,key)
precondition(id==Data((48..<64).map{UInt8($0)}));precondition(text=="手机语音 → Mac：你好 👋\n第二行")
var bad=packet;bad[bad.count-1] ^= 1
var rejected=false;do{_ = try TextCrypto.open(bad,session,key)}catch{rejected=true};precondition(rejected)
rejected=false;do{_ = try TextCrypto.open(packet,Data(repeating:0,count:16),key)}catch{rejected=true};precondition(rejected)
var frames=TextFrames();let chunks=stride(from:0,to:packet.count,by:16).map{Data(packet[$0..<min($0+16,packet.count)])}
for (i,chunk) in chunks.enumerated(){var frame=Data([UInt8(i>>8),UInt8(i&255),UInt8(chunks.count>>8),UInt8(chunks.count&255)]);frame.append(chunk);let result=try frames.add(frame);if i==chunks.count-1{precondition(result==packet)}else{precondition(result==nil)}}
rejected=false;do{_ = try frames.add(Data([0,1,0,2,0]))}catch{rejected=true};precondition(rejected)
print("PASS: Java → Swift encrypted Chinese/emoji, tamper/replay rejection and BLE framing")
