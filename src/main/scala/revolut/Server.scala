package revolut

import scala.collection.mutable

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL.WithBigDecimal._
import spark.Spark
import spark.Spark._

case class Amount(amount: BigDecimal)

class Server {

  private implicit val formats: Formats = DefaultFormats.withBigDecimal

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
    post("/create/*", (request, response) => synchronized {
      val accountId = request.splat()(0)
      if (accounts.contains(accountId)) {
        renderError(s"account $accountId already exists")
      } else {
        accounts += (accountId -> 0)
        okResult
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

    get("/amount/*", (request, response) => synchronized {
      val accountId = request.splat()(0)
      getCurrentAmount(accountId).fold(
        error => renderError(error),
        amount => renderNumberResult(amount)
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

    post("/deposit/*", (request, response) => synchronized {
      val accountId = request.splat()(0)
      val newAmountOrError = for {
        currentAmount <- getCurrentAmount(accountId).right
        depositAmount <- parseAmountJson(request.body()).right
      } yield currentAmount + depositAmount
      newAmountOrError.fold(
        error => renderError(error),
        newAmount => {
          accounts += (accountId -> newAmount)
          okResult
        }
      )
    })

    post("/withdraw/*", (request, response) => synchronized {
      val accountId = request.splat()(0)
      val newAmountOrError = for {
        currentAmount <- getCurrentAmount(accountId).right
        withdrawAmount <- parseAmountJson(request.body()).right
      } yield currentAmount - withdrawAmount
      newAmountOrError
          .filterOrElse(_.signum >= 0, "not enough money")
          .fold(
            error => renderError(error),
            newAmount => {
              accounts += (accountId -> newAmount)
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


    post("/transfer/*/*", (request, response) => synchronized {
      val accountFromId = request.splat()(0)
      val accountToId = request.splat()(1)
      val newAmountsOrError = for {
        _ <- if (accountFromId == accountToId)
          Left(s"can't transfer to the same account: $accountFromId")
        else
          Right()
        amountFrom <- getCurrentAmount(accountFromId)
        amountTo <- getCurrentAmount(accountToId)
        transferAmount <- parseAmountJson(request.body())
      } yield (amountFrom - transferAmount, amountTo + transferAmount)
      newAmountsOrError
          .filterOrElse(
            { case (fromAmount, _) => fromAmount.signum >= 0 },
            "not enough money"
          )
          .fold(
            error => renderError(error),
            { case (newAmountFrom, newAmountTo) =>
              accounts += (accountFromId -> newAmountFrom)
              accounts += (accountToId -> newAmountTo)
              okResult
            }
          )
    })

    Spark.awaitInitialization()
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

  def main(args: Array[String]): Unit = {
    new Server().start()
  }
}
