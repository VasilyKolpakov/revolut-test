package revolut

import com.softwaremill.sttp._
import org.json4s.{DefaultFormats, _}
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.{BeforeAndAfter, EitherValues, FunSuite, Matchers}

case class StringResult(result: String)

case class NumberResult(result: BigDecimal)

case class Error(error: String)

class ServerSpec extends FunSuite with BeforeAndAfter with Matchers with EitherValues {

  private implicit val formats: Formats = DefaultFormats.withBigDecimal
  private implicit val backend = HttpURLConnectionBackend()

  var server: Server = new Server
  server.start()

  test("new accounts have no money") {
    createAccount("test")
    val result = amount("test")
    result.result should equal(0)
  }

  test("'create' method returns an error if account already exists") {
    createAccount("test")
    val response = createAccount("test")
    response.left.value.error should (include("test") and include("exists"))
  }

  test("deposit method adds money to an account") {
    createAccount("test2")
    depost("test2", 100.01)
    val result = amount("test2")
    result.result should equal(100.01)
  }

  private def createAccount(accountId: String) = {
    val createRequest = sttp.post(
      uri"http://localhost:4567/create/$accountId/"
    )
    val response = createRequest.send()
    parse(response.body.right.get).extract[Either[Error, StringResult]]
  }

  private def amount(accountId: String) = {
    val amountRequest = sttp.get(
      uri"http://localhost:4567/amount/$accountId"
    )
    val amountResponse = amountRequest.send()
    val json = amountResponse.body.right.get
    parse(json).extract[NumberResult]
  }

  private def depost(accountId: String, amount: BigDecimal) = {
    val depositRequest = sttp.post(
      uri"http://localhost:4567/deposit/$accountId/"
    ).body(s"""{"amount": $amount}""")
    depositRequest.send()
  }


}
