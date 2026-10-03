import AppKit
import CoreGraphics
let root=URL(fileURLWithPath:CommandLine.arguments[1])
let source=root.appendingPathComponent("Mew/Assets.xcassets/Cat.imageset/cat.png")
let image=NSImage(contentsOf:source)!
var rect=CGRect(origin:.zero,size:image.size)
let original=image.cgImage(forProposedRect:&rect,context:nil,hints:nil)!
let space=CGColorSpace(name:CGColorSpace.sRGB)!
let context=CGContext(data:nil,width:1024,height:1024,bitsPerComponent:8,bytesPerRow:0,space:space,bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue)!
context.setFillColor(CGColor(red:1,green:248/255,blue:239/255,alpha:1));context.fill(CGRect(x:0,y:0,width:1024,height:1024))
let factor=820/max(CGFloat(original.width),CGFloat(original.height));let width=CGFloat(original.width)*factor;let height=CGFloat(original.height)*factor
context.interpolationQuality = .high;context.draw(original,in:CGRect(x:(1024-width)/2,y:(1024-height)/2,width:width,height:height))
let png=NSBitmapImageRep(cgImage:context.makeImage()!).representation(using:.png,properties:[:])!
let dir=root.appendingPathComponent("Mew/Assets.xcassets/AppIcon.appiconset");try FileManager.default.createDirectory(at:dir,withIntermediateDirectories:true)
try png.write(to:dir.appendingPathComponent("icon-1024.png"))
