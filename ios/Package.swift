// swift-tools-version: 5.9
import PackageDescription
let package = Package(name:"MewCore",platforms:[.macOS(.v13),.iOS(.v17)],products:[.library(name:"MewCore",targets:["MewCore"])],targets:[.target(name:"MewCore",path:"Mew/Core"),.testTarget(name:"MewCoreTests",dependencies:["MewCore"],path:"Tests",resources:[.copy("Fixtures")])])
