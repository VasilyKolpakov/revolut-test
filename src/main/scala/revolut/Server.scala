package revolut

object Server {
  def main(args: Array[String]): Unit = {
    /**
     * GET /amount/{account_id} returns the amount of money on the account
     *
     * On success the response should look like this:
     * { "result": 10 }
     *
     * If there was a failure:
     * { "error": "no such account"}
     */

    /**
     * POST /deposit/{account_id} adds money to the account
     * POST /withdraw/{account_id} removes money from the account
     * these methods use request's body
     *
     * "amount" parameter must be greater than zero,
     * and, in case of "/withdraw/" method, less than amount of money on the account
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


    /**
     * POST /transfer/{account_from_id}/{account_to_id} transfers money from account_from to account_to
     * "amount" parameter must be greater than zero and less than amount of money on account_from
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
  }
}
