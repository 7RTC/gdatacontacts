package com.sevenrtc.gdata.contacts;

import com.google.gdata.client.Query;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactFeed;
import com.google.gdata.data.contacts.ContactGroupEntry;
import com.google.gdata.data.contacts.ContactGroupFeed;
import com.google.gdata.data.extensions.ExtendedProperty;
import com.google.gdata.data.extensions.PhoneNumber;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;

/**
 *
 * 
 */
public class GoogleContactsTest {
    
    private static final Pattern mobilesCodeArea11 = Pattern.compile("^(:?0(:?\\d{2})?11)?+([5-9])\\d{7}$");
    private static Set<String> celulares;
    private static Set<String> naoCelulares;
   

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws AuthenticationException, ServiceException, IOException {
        celulares = new TreeSet<>();
        naoCelulares = new TreeSet<>();
        final Properties properties = new Properties();
        properties.load(GoogleContactsTest.class.getResourceAsStream("./Credentials.properties"));
        
        final String username = properties.getProperty("username");
        final ContactsService myService = new ContactsService("7rtc-cellPhoneDigits");
        myService.setUserCredentials(username, properties.getProperty("password"));
        analyzeContacts(myService, username);
        //printAllGroups(myService);
    }
    
    public static void analyzeContacts(ContactsService myService, String username)
            throws ServiceException, IOException {
        
        // Create query and submit a request
        final URL feedUrl = new URL("https://www.google.com/m8/feeds/contacts/" + username + "/full");
        final Query myQuery = new Query(feedUrl);
        myQuery.setMaxResults(Integer.MAX_VALUE);
        myQuery.setStringCustomParameter("group", "http://www.google.com/m8/feeds/groups/" + username + "/base/6");
        final ContactFeed resultFeed = myService.query(myQuery, ContactFeed.class);

        final int size = resultFeed.getTotalResults();
        
        for (ContactEntry contact : resultFeed.getEntries()) {
            verifyPhoneNumbers(myService, contact);
        }
        
        System.out.println("Number of contacts:  " + size);
        
    }
    
    public static ContactEntry verifyPhoneNumbers(ContactsService myService, ContactEntry entry)
            throws ServiceException, IOException {
        
        final String entryName = entry.getTitle().getPlainText();
        boolean mustUpdate = false;
        
        for (PhoneNumber phoneNumber : entry.getPhoneNumbers()) {
            
            final String originalNumber = phoneNumber.getPhoneNumber();
            
            // Check if the number is a mobile with no area code or area code 11
            final Matcher m = mobilesCodeArea11.matcher(originalNumber);
            if (m.matches()) {                
                final int firstDigit = Integer.valueOf(m.group(3));
                final int firstDigitPosition = m.start(3);
                // Insert the digit
                final String withDigit = new StringBuilder(originalNumber)
                        .insert(firstDigitPosition, 9).toString();
                
                // If the number starts with 7, 8 or 9 it is a mobile
                if (firstDigit > 6) {
                    celulares.add(entryName + ": " + originalNumber + " - " + withDigit + " automático");
                    phoneNumber.setPhoneNumber(withDigit);
                    mustUpdate = true;
                }
                // If the number starts with 5 or 6, ask the user for confirmation
                else {
                    final int option = JOptionPane.showConfirmDialog(null, 
                            String.format("É impossível determinar se o número %s,\n"
                            + "pertencente ao contato %s, é um celular.\n"
                            + "Inserir o dígito assim mesmo?\n\n"
                            + "Número após modificação: %s", originalNumber, entryName, withDigit), 
                            "Inserir digito?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (option == JOptionPane.YES_OPTION) {
                        celulares.add(entryName + ": " + originalNumber + " - " + withDigit + " manual");
                        phoneNumber.setPhoneNumber(withDigit);
                        mustUpdate = true;
                    } else {
                        naoCelulares.add(entryName + ": " + originalNumber + " manual");
                    }
                }
            } else {
                naoCelulares.add(entryName + ": " + originalNumber + " automático");
            }        
                
        }
        
        if (mustUpdate) {
            final URL entryUrl = new URL(entry.getSelfLink().getHref());
            final ContactEntry originalEntry = myService.getEntry(entryUrl, ContactEntry.class);
            System.out.println("\nUpdating " + entryName);
            System.out.println("-----------------------------------");
            System.out.printf("|%-15s | %-15s|\n", "BEFORE", "AFTER");
            System.out.println("-----------------------------------");
            for (int i = 0; i < originalEntry.getPhoneNumbers().size(); i++) {
                PhoneNumber before = originalEntry.getPhoneNumbers().get(i);
                PhoneNumber after = entry.getPhoneNumbers().get(i);
                System.out.printf("|%-15s | %-15s|\n", before.getPhoneNumber(), after.getPhoneNumber());
            }
            System.out.println("-----------------------------------");
            final URL editUrl = new URL(entry.getEditLink().getHref());
            /*entry = myService.update(editUrl, entry);*/
        }
        return entry;
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
