package revolut

import scala.collection.mutable

import org.json4s._
import org.json4s.jackson.JsonMethods._
import spark.Spark
import spark.Spark._

class Server {

  case class Amount(amount: BigDecimal)

  private implicit val formats: DefaultFormats = DefaultFormats

  private val accounts = mutable.Map[String, BigDecimal]()

  def start(): Unit = {

    /**
     * POST /create/{account_id} creates account, returns error if account already exists
     *
     * On success the response should look like this:
     * { "result": "OK" }
     *
     * If there was a failure:
     * { "error": "account already exists"}
     */
    post("/create/*", (request, response) => {
      val accountId = request.splat()(0)
      if (accounts.contains(accountId)) {
        """{ "error": "account already exists"}"""
      } else {
        accounts += (accountId -> 0)
        s"""{ "result": "OK" }"""
      }
    })

    /**
     * GET /amount/{account_id} returns the amount of money on the account
     *
     * On success the response should look like this:
     * { "result": 10 }
     *
     * If there was a failure:
     * { "error": "no such account"}
     */

    get("/amount/*", (request, response) => {
      val accountId = request.splat()(0)
      getCurrentAmount(accountId).fold(
        error => s"""{ "error": $error }""",
        amount => s"""{ "result": $amount }"""
      )
    })
    /**
     * POST /deposit/{account_id} adds money to the account
     * POST /withdraw/{account_id} removes money from the account
     * these methods use request's body
     *
     * "amount" parameter must be greater than zero,
     * and, in case of "/withdraw/" method, less than or equal to amount of money on the account
     *
     * Request body example:
     * { "amount": 100 }
     *
     * On success the response should look like this:
     * { "result": "OK" }
     *
     * If there was a failure:
     * { "error": "no such account"}
     */

    post("/deposit/*", (request, response) => {
      val accountId = request.splat()(0)
      val newAmountOrError = for {
        currentAmount <- getCurrentAmount(accountId).right
        depositAmount <- parseAmountJson(request.body()).right
      } yield currentAmount + depositAmount
      newAmountOrError.fold(
        error => s"""{ "error": $error }""",
        newAmount => {
          accounts += (accountId -> newAmount)
          """{ "result": "OK" }"""
        }
      )
    })

    post("/withdraw/*", (request, response) => {
      val accountId = request.splat()(0)
      val newAmountOrError = for {
        currentAmount <- getCurrentAmount(accountId).right
        depositAmount <- parseAmountJson(request.body()).right
      } yield currentAmount + depositAmount
      newAmountOrError.right
        .filter(_.signum >= 0).getOrElse(Left("not enough money"))
        .fold(
          error => s"""{ "error": $error }""",
          newAmount => {
            accounts += (accountId -> newAmount)
            """{ "result": "OK" }"""
          }
        )
    })

    /**
     * POST /transfer/{account_from_id}/{account_to_id} transfers money from account_from to account_to
     * "amount" parameter must be greater than zero and less than or equal to amount of money on account_from
     * these methods use request's body
     *
     * Request body example:
     * { "amount": 100 }
     *
     * On success the response should look like this:
     * { "result": "OK" }
     *
     * If there was a failure:
     * { "error": "no such account"}
     */


    post("/transfer/*/*", (request, response) => {
      val accountFromId = request.splat()(0)
      val accountToId = request.splat()(1)
      val newAmountsOrError = for {
        amountFrom <- getCurrentAmount(accountFromId)
        amountTo <- getCurrentAmount(accountToId)
        transferAmount <- parseAmountJson(request.body())
      } yield (amountFrom - transferAmount, amountTo + transferAmount)
      newAmountsOrError.right
        .filter { case (fromAmount, _) => fromAmount.signum >= 0 }
        .getOrElse(Left("not enough money"))
        .fold(
          error => s"""{ "error": $error }""",
          { case (newAmountFrom, newAmountTo) =>
            accounts += (accountFromId -> newAmountFrom)
            accounts += (accountToId -> newAmountTo)
            """{ "result": "OK" }"""
          }
        )
    })
  }

  def stop(): Unit = {
    Spark.stop()
  }

  private def getCurrentAmount(accountId: String): Either[String, BigDecimal] = {
    accounts.get(accountId) match {
      case Some(amount) => Right(amount)
      case None => Left(s"no such account: $accountId")
    }
  }

  private def parseAmountJson(json: String): Either[String, BigDecimal] = {
    val amountOrError = for {
      jValue <- parseOpt(json).toRight("json parse error").right
      amount <- jValue.extractOpt[Amount].toRight("not a valid json").right
    } yield amount.amount
    amountOrError.right
      .filter(_.signum >= 0)
      .getOrElse(Left("amount can't be negative"))
  }

  def main(args: Array[String]): Unit = {
    new Server().start()
  }
}
