package us.newsrdr

import us.newsrdr.models._
import com.lambdaworks.crypto._

object AuthenticationTools {
  private final val SCRYPT_N = 16384
  private final val SCRYPT_r = 8
  private final val SCRYPT_p = 1
  private final val PASSWORD_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890"
  private final val RANDOM_PASSWORD_LENGTH = 8
  
  private val random = new scala.util.Random
  
  def validatePassword(userInfo: User, providedPassword: String) : Boolean = {
    SCryptUtil.check(providedPassword, userInfo.password)
  }
  
  def hashPassword(password: String) : String = {
    SCryptUtil.scrypt(password, SCRYPT_N, SCRYPT_r, SCRYPT_p)
  }
  
  def randomPassword : String = {
    Stream.continually(random.nextInt(PASSWORD_ALPHABET.size)).map(PASSWORD_ALPHABET).take(RANDOM_PASSWORD_LENGTH).mkString
  }
}