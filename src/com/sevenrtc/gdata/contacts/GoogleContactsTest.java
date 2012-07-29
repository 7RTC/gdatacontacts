package com.sevenrtc.gdata.contacts;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
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
    
    private final ContactsService contactService;
    private final Set<String> labelsToIgnore;
    private final Set<String> mnos;
    private final Pattern mobilesCodeArea11;
    private final String username;
    
    private final Set<String> updated;
    private final Set<String> skipped;


    public GoogleContactsTest() throws AuthenticationException, ServiceException, IOException {
        final Properties properties = new Properties();
        properties.load(GoogleContactsTest.class.getResourceAsStream("./Credentials.properties"));
        
        mobilesCodeArea11 = Pattern.compile("^(:?0(:?\\d{2})?11)?+([5-9])\\d{7}$");
        
        final String[] mnosProperty = properties.getProperty("mnos").split(";");
        mnos = ImmutableSet.copyOf(mnosProperty);
        
        final String[] ignoreListProperty = properties.getProperty("ignoreList").split(";");
        labelsToIgnore = ImmutableSet.copyOf(ignoreListProperty);
        
        username = properties.getProperty("username");
        
        contactService = new ContactsService("7rtc-cellPhoneDigits");
        contactService.setUserCredentials(username, properties.getProperty("password"));
        
        updated = new TreeSet<>();
        skipped = new TreeSet<>();
        
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws AuthenticationException, ServiceException, IOException  {
        
        GoogleContactsTest gct = new GoogleContactsTest();
        gct.analyzeContacts();
        //gct.printAllGroups();
    }
    
    private void analyzeContacts() throws ServiceException, IOException {
        
        // Query Google concats
        final URL feedUrl = new URL("https://www.google.com/m8/feeds/contacts/" + username + "/full");
        final Query myQuery = new Query(feedUrl);
        myQuery.setMaxResults(Integer.MAX_VALUE);
        // Comment the next line to use all groups instead of My Contacts
        myQuery.setStringCustomParameter("group", "http://www.google.com/m8/feeds/groups/" + username + "/base/6");
        final ContactFeed resultFeed = contactService.query(myQuery, ContactFeed.class);

        final int size = resultFeed.getTotalResults();
        
        for (ContactEntry contact : resultFeed.getEntries()) {
            verifyPhoneNumbers(contact);
        }
        
        System.out.println("Number of contacts:  " + size);
        
    }
    
    public ContactEntry verifyPhoneNumbers(ContactEntry entry)
            throws ServiceException, IOException {
        
        final String entryName = entry.getTitle().getPlainText();
        boolean mustUpdate = false;
        
        for (PhoneNumber phoneNumber : entry.getPhoneNumbers()) {
            
            final String originalNumber = phoneNumber.getPhoneNumber();
            final String label = phoneNumber.getLabel();
            
            // Check if the number is a mobile with no area code or area code 11
            final Matcher m = mobilesCodeArea11.matcher(originalNumber);
            if (m.matches()) {                
                final int firstDigit = Integer.valueOf(m.group(3));
                final int firstDigitPosition = m.start(3);
                // Insert the digit
                final String withDigit = new StringBuilder(originalNumber)
                        .insert(firstDigitPosition, 9).toString();
                
                // If the number starts with 8 or 9 it is a mobile
                if (firstDigit > 7) {
                    updated.add(entryName + ": " + originalNumber + " - " + withDigit + " automático");
                    phoneNumber.setPhoneNumber(withDigit);
                    mustUpdate = true;
                }
                // If the number starts with 5, 6 or 7 
                else {
                    boolean hasLabel = !Strings.isNullOrEmpty(label);
                    // Try to detect the mno from the label
                    if (hasLabel && mnos.contains(label)) {
                        updated.add(entryName + ": " + originalNumber + " - " + withDigit + " automático");
                        phoneNumber.setPhoneNumber(withDigit);
                        mustUpdate = true;
                    } 
                    // Skip labels from non mobile operators
                    else if (hasLabel && labelsToIgnore.contains(label)) {
                        skipped.add(entryName + ": " + originalNumber + " automático");
                    }
                    // Else ak the user
                    else {
                        if (hasLabel) {
                            System.out.println(label + " : " +  originalNumber);
                        }
                        final int option = JOptionPane.showConfirmDialog(null,
                                String.format("É impossível determinar se o número %s,\n"
                                + "pertencente ao contato %s, é um celular.\n"
                                + "Inserir o dígito assim mesmo?\n\n"
                                + "Número após modificação: %s", originalNumber, entryName, withDigit),
                                "Inserir digito?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                        if (option == JOptionPane.YES_OPTION) {
                            updated.add(entryName + ": " + originalNumber + " - " + withDigit + " manual");
                            phoneNumber.setPhoneNumber(withDigit);
                            mustUpdate = true;
                        } else {
                            skipped.add(entryName + ": " + originalNumber + " manual");
                        }
                    }
                }
            } else {
                skipped.add(entryName + ": " + originalNumber + " automático");
            }        
                
        }
        
        if (mustUpdate) {
            final URL entryUrl = new URL(entry.getSelfLink().getHref());
            final ContactEntry originalEntry = contactService.getEntry(entryUrl, ContactEntry.class);
            System.out.println("\nAtualizando " + entryName);
            System.out.println("+---------------------------------+");
            System.out.printf("|%-15s | %-15s|\n", "ANTES", "DEPOIS");
            System.out.println("+---------------------------------+");
            for (int i = 0; i < originalEntry.getPhoneNumbers().size(); i++) {
                PhoneNumber before = originalEntry.getPhoneNumbers().get(i);
                PhoneNumber after = entry.getPhoneNumbers().get(i);
                System.out.printf("|%-15s | %-15s|\n", before.getPhoneNumber(), after.getPhoneNumber());
            }
            System.out.println("+---------------------------------+");
            final URL editUrl = new URL(entry.getEditLink().getHref());
            // Comment the next line to avoid updating your contact list over the first run
            entry = contactService.update(editUrl, entry);
        }
        return entry;
    }
    
    private void printAllGroups()
            throws ServiceException, IOException {
        // Request the feed
        URL feedUrl = new URL("https://www.google.com/m8/feeds/groups/" + username + "/full");
        ContactGroupFeed resultFeed = contactService.getFeed(feedUrl, ContactGroupFeed.class);
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
