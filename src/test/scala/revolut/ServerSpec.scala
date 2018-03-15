package revolut

import com.softwaremill.sttp._
import org.json4s.{DefaultFormats, _}
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

case class StringResult(result: String)

case class NumberResult(result: BigDecimal)

class ServerSpec extends FunSuite with BeforeAndAfter with Matchers {

  private implicit val formats: Formats = DefaultFormats.withBigDecimal
  private implicit val backend = HttpURLConnectionBackend()

  var server: Server = new Server
  server.start()

  test("new accounts have no money") {
    val createRequest = sttp.post(
      uri"http://localhost:4567/create/test/"
    )
    createRequest.send()
    val amountRequest = sttp.get(
      uri"http://localhost:4567/amount/test"
    )
    val amountResponse = amountRequest.send()
    val json = amountResponse.body.right.get
    val result = parse(json).extract[NumberResult]
    result.result should equal(0)
  }

  test("deposit method adds money to an account") {
    val createRequest = sttp.post(
      uri"http://localhost:4567/create/test2/"
    )
    createRequest.send()

    val depositRequest = sttp.post(
      uri"http://localhost:4567/deposit/test2/"
    ).body("""{"amount": 100.01}""")
    depositRequest.send()

    val amountRequest = sttp.get(
      uri"http://localhost:4567/amount/test2"
    )
    val amountResponse = amountRequest.send()
    val json = amountResponse.body.right.get
    val result = parse(json).extract[NumberResult]
    result.result should equal(100.01)
  }

}
