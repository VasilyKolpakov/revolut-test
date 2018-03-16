package revolut

import java.util.UUID
import java.util.concurrent.Executors

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.Random

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

  var accountId: String = _

  before {
    accountId = UUID.randomUUID().toString
  }

  /**
   * 'create' method
   */

  test("new accounts have no money") {
    createAccount(accountId)
    val result = amount(accountId)
    result.right.value.result should equal(0)
  }

  test("'create' method returns an error if account already exists") {
    createAccount(accountId)
    val response = createAccount(accountId)
    response.left.value.error should (include(accountId) and include("exists"))
  }

  /**
   * 'amount' method
   */

  test("'amount' method returns an error if account doesn't exist") {
    val response = amount(accountId)
    response.left.value.error should include(accountId)
  }

  /**
   * 'deposit' method
   */

  test("'deposit' method adds money to an account") {
    createAccount(accountId)
    deposit(accountId, 100.01)
    val result = amount(accountId)
    result.right.value.result should equal(100.01)
  }

  test("'deposit' method returns error on negative amounts") {
    createAccount(accountId)
    val response = deposit(accountId, -100.01)
    response.left.value.error should include("negative")
  }

  test("'deposit' method returns error on bad json") {
    createAccount(accountId)
    val response = deposit(accountId, 100.01, badRequest = true)
    response.left.value.error should include("json")
  }

  test("'deposit' method returns error on non-existent account") {
    val response = deposit(accountId, 100.01, badRequest = true)
    response.left.value.error should include(accountId)
  }

  /**
   * 'withdraw' method
   */

  test("'withdraw' method removes money from an account") {
    createAccount(accountId)
    deposit(accountId, 100.01)
    withdraw(accountId, 0.01)
    val result = amount(accountId)
    result.right.value.result should equal(100)
  }

  test("'withdraw' method returns error if there is not enough money") {
    createAccount(accountId)
    val response = withdraw(accountId, 100.01)
    response.left.value.error should include("not enough")
  }

  test("'withdraw' method returns error on bad json") {
    createAccount(accountId)
    val response = withdraw(accountId, 100.01, badRequest = true)
    response.left.value.error should include("json")
  }

  /**
   * 'transfer' method
   */

  private def accountFrom = accountId + "_from"

  private def accountTo = accountId + "_to"

  test("'transfer' method moves money") {
    createAccount(accountFrom)
    createAccount(accountTo)
    deposit(accountFrom, 100)
    transfer(accountFrom, accountTo, 100)
    amount(accountFrom).right.value.result should equal(0)
    amount(accountTo).right.value.result should equal(100)
  }

  test("'transfer' method returns error if there is not enough money") {
    createAccount(accountFrom)
    createAccount(accountTo)
    deposit(accountFrom, 10)
    val response = transfer(accountFrom, accountTo, 100)
    response.left.value.error should include("enough")
    amount(accountFrom).right.value.result should equal(10)
    amount(accountTo).right.value.result should equal(0)
  }

  test("'transfer' method returns error 'from' and 'to' accounts are the same") {
    createAccount(accountId)
    deposit(accountId, 100)
    val response = transfer(accountId, accountId, 10)
    response.left.value.error should (include(accountId) and include("same"))
    amount(accountId).right.value.result should equal(100)
  }

  /**
   * multithreading
   */

  test("multithreading") {
    val threadPool = Executors.newFixedThreadPool(10, (runnable: Runnable) => {
      val thread = new Thread(runnable)
      thread.setDaemon(true)
      thread
    })
    implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(threadPool)
    val accounts = (1 to 5).map {
      accountId + "_" + _
    }
    for (account <- accounts) {
      createAccount(account)
      deposit(account, 100)
    }
    val random = new Random()

    def randomAccount = accounts(random.nextInt(accounts.size))

    val futures = for (_ <- 0 to 500) yield {
      Future {
        transfer(randomAccount, randomAccount, 1)
      }
    }
    Await.ready(Future.sequence(futures), Duration.Inf)
    val sum = accounts.map(account => amount(account).right.get.result).sum
    sum should equal(accounts.size * 100)
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
    parse(json).extract[Either[Error, NumberResult]]
  }

  private def deposit(accountId: String, amount: BigDecimal, badRequest: Boolean = false) = {
    val depositRequest = sttp.post(
      uri"http://localhost:4567/deposit/$accountId/"
    ).body(s"""{"amount": $amount""" + (if (badRequest) "" else "}"))
    val response = depositRequest.send()
    val json = response.body.right.get
    parse(json).extract[Either[Error, StringResult]]
  }

  private def withdraw(accountId: String, amount: BigDecimal, badRequest: Boolean = false) = {
    val depositRequest = sttp.post(
      uri"http://localhost:4567/withdraw/$accountId/"
    ).body(s"""{"amount": $amount""" + (if (badRequest) "" else "}"))
    val response = depositRequest.send()
    val json = response.body.right.get
    parse(json).extract[Either[Error, StringResult]]
  }

  private def transfer(accountIdFrom: String, accountIdTo: String, amount: BigDecimal, badRequest: Boolean = false) = {
    val depositRequest = sttp.post(
      uri"http://localhost:4567/transfer/$accountIdFrom/$accountIdTo/"
    ).body(s"""{"amount": $amount""" + (if (badRequest) "" else "}"))
    val response = depositRequest.send()
    val json = response.body.right.get
    parse(json).extract[Either[Error, StringResult]]
  }

}
