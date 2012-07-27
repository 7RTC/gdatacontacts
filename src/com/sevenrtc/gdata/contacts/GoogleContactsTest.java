package com.sevenrtc.gdata.contacts;

import com.google.common.base.Strings;
import com.google.gdata.client.Query;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactFeed;
import com.google.gdata.data.contacts.ContactGroupEntry;
import com.google.gdata.data.contacts.ContactGroupFeed;
import com.google.gdata.data.extensions.Email;
import com.google.gdata.data.extensions.ExtendedProperty;
import com.google.gdata.data.extensions.PhoneNumber;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

/**
 *
 * 
 */
public class GoogleContactsTest {
   

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws AuthenticationException, ServiceException, IOException {
        Properties properties = new Properties();
        properties.load(GoogleContactsTest.class.getResourceAsStream("./Credentials.properties"));
        
        String username = properties.getProperty("username");
        ContactsService myService = new ContactsService("7rtc-cellPhoneDigits");
        myService.setUserCredentials(username, properties.getProperty("password"));
        printAllContacts(myService, username);
        //printAllGroups(myService);
    }
    
    public static void printAllContacts(ContactsService myService, String username)
            throws ServiceException, IOException {
        
        // Create query and submit a request
        URL feedUrl = new URL("https://www.google.com/m8/feeds/contacts/" + username + "/full");
        Query myQuery = new Query(feedUrl);
        myQuery.setMaxResults(Integer.MAX_VALUE);
        myQuery.setStringCustomParameter("group", "http://www.google.com/m8/feeds/groups/" + username + "/base/6");
        ContactFeed resultFeed = myService.query(myQuery, ContactFeed.class);
 
        // Print the results
        System.out.println(resultFeed.getTitle().getPlainText());
        System.out.println("-------------------");
        
        final int size = resultFeed.getTotalResults();
        
        for (int i = 0; i < size; i++) {
            ContactEntry entry = resultFeed.getEntries().get(i);
            System.out.println(entry.getTitle().getPlainText());
            System.out.println();

            if (entry.getTitle().getPlainText().equals("Adriana Kinoshita")) {
                setDigit(myService, entry);
            }
            System.out.println(" Email addresses:");
            for (Email email : entry.getEmailAddresses()) {
                System.out.print("  " + email.getAddress());
                if (email.getLabel() != null) {
                    System.out.print(" label:" + email.getLabel());
                }
                if (email.getPrimary()) {
                    System.out.print(" (primary) ");
                }
                System.out.print("\n");
            }
            
            System.out.println(" Phone  numbers:");
            for (PhoneNumber phoneNumber : entry.getPhoneNumbers()) {
                if (!Strings.isNullOrEmpty(phoneNumber.getLabel())) {
                    System.out.printf("  %s : %s\n", phoneNumber.getLabel(), phoneNumber.getPhoneNumber()); 
                } else {
                    System.out.printf("  Tel : %s\n", phoneNumber.getPhoneNumber());
                }
                
            }
            System.out.println("------------------------------");            
        }
        
        
        System.out.println("Number of contacts:  " + size);
        
    }
    
    public static ContactEntry setDigit(ContactsService myService, ContactEntry entry)
            throws ServiceException, IOException {
        
        for (PhoneNumber phoneNumber : entry.getPhoneNumbers()) {
            if (phoneNumber.getPhoneNumber().startsWith("6")
                    || phoneNumber.getPhoneNumber().startsWith("7")
                    || phoneNumber.getPhoneNumber().startsWith("8")
                    || phoneNumber.getPhoneNumber().startsWith("9")) {
                phoneNumber.setPhoneNumber("9" + phoneNumber.getPhoneNumber());
            }            
                
        }
        URL editUrl = new URL(entry.getEditLink().getHref());
        return myService.update(editUrl, entry);
    }
    
    public static void printAllGroups(ContactsService myService)
            throws ServiceException, IOException {
        // Request the feed
        URL feedUrl = new URL("https://www.google.com/m8/feeds/groups/a.accioly@7rtc.com/full");
        ContactGroupFeed resultFeed = myService.getFeed(feedUrl, ContactGroupFeed.class);
        // Print the results
        System.out.println(resultFeed.getTitle().getPlainText());

        for (int i = 0; i < resultFeed.getEntries().size(); i++) {
            ContactGroupEntry groupEntry = resultFeed.getEntries().get(i);
            System.out.println("Id: " + groupEntry.getId());
            System.out.println("Group Name: " + groupEntry.getTitle().getPlainText());
            System.out.println("Last Updated: " + groupEntry.getUpdated());
            System.out.println("Extended Properties:");
            for (ExtendedProperty property : groupEntry.getExtendedProperties()) {
                if (property.getValue() != null) {
                    System.out.println("  " + property.getName() + "(value) = "
                            + property.getValue());
                } else if (property.getXmlBlob() != null) {
                    System.out.println("  " + property.getName() + "(xmlBlob) = "
                            + property.getXmlBlob().getBlob());
                }
            }
            System.out.println("Self Link: " + groupEntry.getSelfLink().getHref());
            if (!groupEntry.hasSystemGroup()) {
                // System groups do not have an edit link
                System.out.println("Edit Link: " + groupEntry.getEditLink().getHref());
                System.out.println("ETag: " + groupEntry.getEtag());
            }
            if (groupEntry.hasSystemGroup()) {
                System.out.println("System Group Id: "
                        + groupEntry.getSystemGroup().getId());
            }
        }
    }
}
