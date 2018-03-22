package revolut

import scala.collection.mutable

class AccountsDB {
  private val accounts = mutable.Map[String, BigDecimal]()

  def createAccount(accountId: String): Either[String, Unit] = {
    if (accounts.contains(accountId)) {
      Left(s"account $accountId already exists")
    } else {
      accounts += (accountId -> 0)
      Right()
    }
  }

  def getCurrentAmount(accountId: String): Either[String, BigDecimal] = {
    accounts.get(accountId) match {
      case Some(amount) => Right(amount)
      case None => Left(s"no such account: $accountId")
    }
  }

  def deposit(accountId: String, depositAmount: BigDecimal): Either[String, BigDecimal] = {
    for {
      currentAmount <- getCurrentAmount(accountId).right
    } yield {
      val newAmount = currentAmount + depositAmount
      accounts += (accountId -> newAmount)
      newAmount
    }
  }

  def withdraw(accountId: String, withdrawAmount: BigDecimal): Either[String, BigDecimal] = {
    for {
      currentAmount <- getCurrentAmount(accountId).right
      newAmount <-
          if (currentAmount >= withdrawAmount)
            Right(currentAmount - withdrawAmount)
          else
            Left("not enough money")
    } yield {
      accounts += (accountId -> newAmount)
      newAmount
    }
  }

  def transfer(
      accountFromId: String,
      accountToId: String,
      transferAmount: BigDecimal): Either[String, (BigDecimal, BigDecimal)] = {
    for {
      _ <- if (accountFromId == accountToId)
        Left(s"can't transfer to the same account: $accountFromId")
      else
        Right()
      amountFrom <- getCurrentAmount(accountFromId)
      amountTo <- getCurrentAmount(accountToId)
      newAmounts <-
          if (amountFrom >= transferAmount)
            Right((amountFrom - transferAmount, amountTo + transferAmount))
          else
            Left("not enough money")
    } yield {
      val (newAmountFrom, newAmountTo) = newAmounts
      accounts += (accountFromId -> newAmountFrom)
      accounts += (accountToId -> newAmountTo)
      newAmounts
    }
  }
}
