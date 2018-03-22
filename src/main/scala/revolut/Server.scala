package revolut

import com.typesafe.scalalogging.Logger
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL.WithBigDecimal._
import spark.Spark
import spark.Spark._

case class Amount(amount: BigDecimal)

object Server {
  def main(args: Array[String]): Unit = {
    new Server().start()
  }
}

class Server {

  private val log = Logger(getClass)

  private implicit val formats: Formats = DefaultFormats.withBigDecimal

  private val accountsDb = new AccountsDB

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
      accountsDb.createAccount(accountId).fold(
        error => {
          log.debug(s"error during account creation: $error")
          renderError(error)
        },
        _ => {
          log.debug(s"registered account $accountId")
          okResult
        }
      )
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
      accountsDb.getCurrentAmount(accountId).fold(
        error => {
          log.debug(s"'amount' method error: $error")
          renderError(error)
        },
        amount => {
          log.debug(s"'amount' method success: $accountId - $amount")
          renderNumberResult(amount)
        }
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
        depositAmount <- parseAmountJson(request.body()).right
        newAmount <- accountsDb.deposit(accountId, depositAmount).right
      } yield newAmount
      newAmountOrError.fold(
        error => {
          log.debug(s"'deposit' method error: $error")
          renderError(error)
        },
        newAmount => {
          log.debug(s"'deposit' method success: $accountId - $newAmount")
          okResult
        }
      )
    })

    post("/withdraw/*", (request, response) => {
      val accountId = request.splat()(0)
      val newAmountOrError = for {
        withdrawAmount <- parseAmountJson(request.body()).right
        newAmount <- accountsDb.withdraw(accountId, withdrawAmount).right
      } yield newAmount
      newAmountOrError
          .fold(
            error => {
              log.debug(s"'withdraw' method error: $error")
              renderError(error)
            },
            newAmount => {
              log.debug(s"'withdraw' method success: $accountId - $newAmount")
              okResult
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
        transferAmount <- parseAmountJson(request.body())
        newAmounts <- accountsDb.transfer(accountFromId, accountToId, transferAmount)
      } yield newAmounts
      newAmountsOrError
          .fold(
            error => {
              log.debug("'transfer' method error: {}", error)
              renderError(error)
            },
            { case (newAmountFrom, newAmountTo) =>
              log.debug(s"'withdraw' method success: " +
                  s"$accountFromId - $newAmountFrom ; $accountToId - $newAmountTo"
              )
              okResult
            }
          )
    })

    Spark.awaitInitialization()
  }

  private def parseAmountJson(json: String): Either[String, BigDecimal] = {
    val amountOrError = for {
      jValue <- parseOpt(json).toRight("json parse error").right
      amount <- jValue.extractOpt[Amount].toRight("not a valid json").right
    } yield amount.amount
    amountOrError.filterOrElse(_.signum >= 0, "amount can't be negative")
  }

  private def renderError(message: String) = {
    pretty(render("error" -> message))
  }

  private def okResult = {
    pretty(render("result" -> "OK"))
  }

  private def renderNumberResult(number: BigDecimal) = {
    pretty(render("result" -> number))
  }

}
