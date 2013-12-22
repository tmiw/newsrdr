package us.newsrdr

import us.newsrdr.models._
import com.lambdaworks.crypto._

object AuthenticationTools {
  private final val SCRYPT_N = 16384
  private final val SCRYPT_r = 8
  private final val SCRYPT_p = 1
    
  def validatePassword(userInfo: User, providedPassword: String) : Boolean = {
    SCryptUtil.check(providedPassword, userInfo.password)
  }
  
  def hashPassword(password: String) : String = {
    SCryptUtil.scrypt(password, SCRYPT_N, SCRYPT_r, SCRYPT_p)
  }
}