package de.dal33t.powerfolder.clientserver;

import java.util.Collection;

import de.dal33t.powerfolder.message.clientserver.AccountDetails;
import de.dal33t.powerfolder.security.Account;

/**
 * Contins all methods to modify/alter, create or notify users.
 * <p>
 * FIXME: Move to correct package: de.dal33t.powerfolder.clientserver
 * 
 * @author <a href="mailto:sprajc@riege.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public interface UserService {
    static final String SERVICE_ID = "userservice.id";

    /**
     * Tries to register a new account.
     * 
     * @param username
     *            the username
     * @param password
     *            the password
     * @param newsLetter
     *            true if the users wants to subscribe to the newsletter.
     * @return if the registration was successfully. false is not possible or
     *         already taken even if password match.
     */
    boolean register(String username, String password, boolean newsLetter);

    /**
     * Logs in from a remote location.
     * 
     * @param username
     * @param passwordMD5
     *            the password mixed with the salt as MD5
     * @param salt
     *            the salt - a random string.
     * @return the Account with this username or null if login failed.
     */
    boolean login(String username, String passwordMD5, String salt);

    /**
     * @return Account details about the currently logged in user.
     */
    AccountDetails getAccountDetails();

    /**
     * @param filterModel
     *            the filter to apply
     * @return the filtered list of accounts.
     */
    Collection<AccountDetails> getAccounts(AccountFilterModel filterModel);

    /**
     * @param username
     * @return the with the given username or null if not found
     */
    Account findByUsername(String username);

    /**
     * Saves or updates the given Account.
     * 
     * @param user
     */
    void store(Account user);

    /**
     * Removes a user with the given username from the database
     * 
     * @param username
     * @return true if the user existed and is now removed. false if the
     *         username could not be found.
     */
    boolean delete(String username);

    /**
     * Checks the givens accounts for excess usage
     * 
     * @param usernames
     *            the username of the users to check.
     */
    void checkAccounts(String... usernames);

    /**
     * @return all license key content for this account. or null if no key was
     *         found.
     */
    String[] getLicenseKeyContents();
}
