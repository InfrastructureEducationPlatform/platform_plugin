package com.example.demo

class Block {
    var x:Int = 0
    var y:Int = 0
    var id:String = ""
    var name:String = ""
    var tags = ArrayList<String>()
    var type:String = ""
    var description:String = ""
    var advancedMeta:String = ""
    var virtualMachineFeatures:Features = Features("low", "ubuntu", "korea")

    inner class Features(s: String, s1: String, s2: String) {
        var tier:String = ""
        var osType:String = ""
        var region:String = ""
    }
}