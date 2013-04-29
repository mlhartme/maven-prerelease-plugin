/* Copyright (c) 1&1. All Rights Reserved. */

package net.oneandone.maven.plugins.prerelease.util;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.util.Date;
import java.util.Properties;

public class Mailer {
    private final String smtphost;
    private final String username;
    private final String password;

    public Mailer() {
        this("mintern.schlund.de", "", "");
    }
    public Mailer(String smtphost, String username, String password) {
        this.smtphost = smtphost;
        this.username = username;
        this.password = password;
    }

    public void send(String from, String to, String subject, String text, File ... attachments) throws MessagingException {
        send(from, new String[] { to }, subject, text, attachments);
    }

    public void send(String from, String[] to, String subject, String text, File ... attachments) throws MessagingException {
        Properties props;
        MimeMessage msg;
        Session session;
        Multipart multipart;
        int i;

        props = new Properties();
        props.put("mail.smtp.host", smtphost);
        session = Session.getDefaultInstance(props, null);

        msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        msg.setRecipients(RecipientType.TO, addresses(to));
        msg.setSubject(subject);
        msg.setSentDate(new Date());

        if (attachments.length == 0) {
            // avoid evolution attachment marker with no attachments
            msg.setText(text);
        } else {
            multipart = new MimeMultipart();
            addText(multipart, text);
            for (i = 0; i < attachments.length; i++) {
                addAttachment(multipart, attachments[i]);
            }
            msg.setContent(multipart);
        }
        Transport.send(msg);
    }

    private static Address[] addresses(String[] tos) throws AddressException {
        Address[] result;

        result = new Address[tos.length];
        for (int i = 0; i < tos.length; i++) {
            result[i] = new InternetAddress(tos[i]);
        }
        return result;
    }

    private static void addText(Multipart dest, String text) throws MessagingException {
        BodyPart messageBodyPart;

        messageBodyPart = new MimeBodyPart();
        messageBodyPart.setText(text);
        dest.addBodyPart(messageBodyPart);
    }

    private void addAttachment(Multipart dest, File file) throws MessagingException {
        MimeBodyPart part;
        String filename;
        DataSource source;

        filename = file.getPath();
        part = new MimeBodyPart();
        source = new FileDataSource(filename);
        part.setDataHandler(new DataHandler(source));
        part.setFileName(file.getName());
        dest.addBodyPart(part);
    }

    @Override
    public String toString() {
        return "Mailer(smtphost=" + smtphost + ", username=" + username + ", password=" + password + ")";
    }
}
