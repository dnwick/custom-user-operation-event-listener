package org.wso2.carbon.custom.notification.sender.internal;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.custom.notification.sender.CustomNotificationSenderListener;
import org.wso2.carbon.user.core.listener.UserOperationEventListener;

import java.util.Properties;


/**
 *
 * @scr.component name="CustomNotificationSenderComponent"
 * immediate="true"
 */
public class CustomNotificationSenderComponent {

    private ServiceRegistration serviceRegistration = null;

    private static CustomNotificationSenderListener listener;

    private static Log log = LogFactory.getLog(CustomNotificationSenderComponent.class);

    protected void activate(ComponentContext context) {

        try {
            listener = new CustomNotificationSenderListener();
            //register the custom listener as an OSGI service.
            serviceRegistration = context.getBundleContext().registerService(
                    UserOperationEventListener.class.getName(), listener, new Properties());

            log.info("Custom Notification sender listener bundle activated");
        }catch (Throwable t) {
            log.error("Error occurred registering bundle : " + t.getMessage());
        }
    }

    protected void deactivate(ComponentContext context) {
        log.debug("Custom Notification sender listener bundle is de-activated");
    }
}
