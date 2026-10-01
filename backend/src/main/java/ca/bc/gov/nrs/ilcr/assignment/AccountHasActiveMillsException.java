package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when an account deactivation is attempted while the user still holds at least one active
 * mill assignment.
 *
 * <p>The guard exists because clearing the assignments is the actual "switch this user off"
 * workflow; the account flag alone neither grants nor removes access. Refusing here keeps an
 * administrator from believing they have revoked something they have not. The flag is left
 * untouched, exactly as the legacy screen left it.
 */
public class AccountHasActiveMillsException extends BusinessException {

  /**
   * Refuses the deactivation, naming the user in the message.
   *
   * @param userGuid the user whose deactivation was refused
   */
  public AccountHasActiveMillsException(String userGuid) {
    // The legacy sentence names the user three ways and only the id is known here, so the two name
    // positions render empty until the directory can fill them.
    super(
        HttpStatus.CONFLICT,
        "error.user.deactivate.hasactivemills",
        new Object[] {userGuid, "", ""});
  }
}
