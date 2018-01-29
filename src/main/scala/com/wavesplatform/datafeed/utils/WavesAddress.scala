package com.wavesplatform.datafeed.utils

import scorex.crypto.hash.Blake2b256
import scorex.crypto.hash.Keccak256
import scorex.crypto.encode.Base58

object WavesAddress {

  var chainId: Byte = 'T'.toByte

  private def hashChain(noncedSecret: Array[Byte]): Array[Byte] = Keccak256.hash(Blake2b256.hash(noncedSecret))

   def fromPublicKey(publicKeyBase58: String): String = {
    val publicKey = Base58.decode(publicKeyBase58).get
    val addrVersion: Byte = 1
    val unhashedAddress = addrVersion +: chainId +: hashChain(publicKey).take(20)
    val address = Base58.encode(unhashedAddress ++ hashChain(unhashedAddress).take(4))
    new String(address)
  }

}