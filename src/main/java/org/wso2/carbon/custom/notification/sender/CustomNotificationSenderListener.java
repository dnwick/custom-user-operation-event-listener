package org.wso2.carbon.custom.notification.sender;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.base.IdentityException;
import org.wso2.carbon.identity.mgt.IdentityMgtConfig;
import org.wso2.carbon.identity.mgt.NotificationSender;
import org.wso2.carbon.identity.mgt.NotificationSendingModule;
import org.wso2.carbon.identity.mgt.dto.NotificationDataDTO;
import org.wso2.carbon.identity.mgt.dto.UserIdentityClaimsDO;
import org.wso2.carbon.identity.mgt.mail.DefaultEmailSendingModule;
import org.wso2.carbon.identity.mgt.mail.Notification;
import org.wso2.carbon.identity.mgt.mail.NotificationBuilder;
import org.wso2.carbon.identity.mgt.mail.NotificationData;
import org.wso2.carbon.identity.mgt.store.UserIdentityDataStore;
import org.wso2.carbon.identity.mgt.util.Utils;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.common.AbstractUserOperationEventListener;
import org.wso2.carbon.user.core.util.UserCoreUtil;

public class CustomNotificationSenderListener extends AbstractUserOperationEventListener{

    private static final Log log = LogFactory.getLog(CustomNotificationSenderListener.class);

    @Override
    public int getExecutionOrderId() {

        //This listener should execute before the IdentityMgtEventListener
        //Hence the number should be < 1357 (Execution order ID of IdentityMgtEventListener)
        return 1356;
    }

    @Override
    public boolean doPostAuthenticate(String userName, boolean authenticated, UserStoreManager userStoreManager)
            throws UserStoreException {

        if (isMaxFailedLoginAttemptsExceeded(userName, authenticated, userStoreManager)) {
            int tenantId = userStoreManager.getTenantId();
            String userStoreDomain = userStoreManager.getRealmConfiguration().getUserStoreProperty(
                    UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
            sendNotification(tenantId, userName, userStoreDomain);
        }
        return true;
    }

    /**
     *
     * @param userName
     * @param authenticated
     * @param userStoreManager
     * @return true - if max login attempts are exceeded, false - otherwise
     * @throws UserStoreException
     */
    private boolean isMaxFailedLoginAttemptsExceeded(String userName, boolean authenticated, UserStoreManager
            userStoreManager) throws UserStoreException {

        UserIdentityDataStore module = IdentityMgtConfig.getInstance().getIdentityDataStore();
        IdentityMgtConfig config = IdentityMgtConfig.getInstance();
        UserIdentityClaimsDO userIdentityDTO = module.load(userName, userStoreManager);

        if (userIdentityDTO == null) {
            userIdentityDTO = new UserIdentityClaimsDO(userName);
        }

        if (!authenticated && config.isAuthPolicyAccountLockOnFailure()) {

            // reading the max allowed #of failure attempts.
            String domainName = userStoreManager.getRealmConfiguration().getUserStoreProperty(
                    UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
            String usernameWithDomain = UserCoreUtil.addDomainToName(userName, domainName);
            boolean isUserExistInCurrentDomain = userStoreManager.isExistingUser(usernameWithDomain);

            if (isUserExistInCurrentDomain) {
                if (userIdentityDTO.getFailAttempts() + 1 >= config.getAuthPolicyMaxLoginAttempts()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getEmailTemplate() {

        String subject = "User Account Lock Notification";
        String body = "Hi {first-name} {last-name},\n\n" +
                      "Your account {user-name} is locked due to exceeding the maximum login attempts.";
        String footer = "Regards,\n" +
                        "{sender}";

        //Note that the email subject, body and footer each are separated by '|' sign.
        String template = subject + "|" + body + "|" + footer;

        return template;
    }

    private void sendNotification(int tenantId, String userName, String userStoreDomain) throws UserStoreException {

        if (log.isDebugEnabled()) {
            log.debug("Starting sending notification to " +
                      "  user :" + userName +
                      ", tenant :" + tenantId
            );
        }

        NotificationDataDTO notificationData = new NotificationDataDTO();

        NotificationData emailNotificationData = new NotificationData();

        String emailTemplate = null;
        String firstName = null;
        String lastName = null;
        String email = null;

        try {

            //Retrieve claims for the user.
            firstName = Utils.getClaimFromUserStoreManager(userStoreDomain + "/" + userName, tenantId,
                                                           UserCoreConstants.ClaimTypeURIs.GIVEN_NAME);
            lastName = Utils.getClaimFromUserStoreManager(userStoreDomain + "/" + userName, tenantId,
                                                          UserCoreConstants.ClaimTypeURIs.SURNAME);
            email = Utils.getClaimFromUserStoreManager(userStoreDomain + "/" + userName, tenantId,
                                                       UserCoreConstants.ClaimTypeURIs.EMAIL_ADDRESS);

            if (email == null) {
                log.error("email address for " + userStoreDomain + "/" + userName + "not set.");
                return;
            }

            if (log.isDebugEnabled()) {
                log.debug("Retrieved claim " + UserCoreConstants.ClaimTypeURIs.GIVEN_NAME + ": "+ firstName + "\n" +
                          "Retrieved claim " + UserCoreConstants.ClaimTypeURIs.SURNAME + ": " + lastName + "\n" +
                          "Retrieved claim " + UserCoreConstants.ClaimTypeURIs.EMAIL_ADDRESS + ": " + email);
            }

        } catch (IdentityException e) {
            log.error("Could not retrieve user claims for :" + userName, e);
        }

        emailTemplate = getEmailTemplate();

        emailNotificationData.setSendTo(email);

        //replace template tags with the relevant values.
        emailNotificationData.setTagData("first-name", firstName);
        emailNotificationData.setTagData("last-name", lastName);
        emailNotificationData.setTagData("user-name", userName);
        emailNotificationData.setTagData("sender", "Admin");

        Notification emailNotification = null;

        try {
            emailNotification = NotificationBuilder.createNotification("EMAIL", emailTemplate, emailNotificationData);
        } catch (Exception e) {
            log.error("Could not create the email notification for template : " + emailTemplate, e);
            return;
        }

        NotificationSender sender = new NotificationSender();

        NotificationSendingModule notificationSendingModule = new DefaultEmailSendingModule();
        if (IdentityMgtConfig.getInstance().isNotificationInternallyManaged()) {
            notificationSendingModule.setNotificationData(notificationData);
            notificationSendingModule.setNotification(emailNotification);
            if (log.isDebugEnabled()) {
                log.debug("Sending email notification for account locking.\n" + emailNotification);
            }
            sender.sendNotification(notificationSendingModule);
            notificationData.setNotificationSent(true);
        }
    }


}
