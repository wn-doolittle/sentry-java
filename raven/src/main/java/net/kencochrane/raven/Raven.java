package net.kencochrane.raven;

import net.kencochrane.raven.connection.AsyncConnection;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.connection.HttpConnection;
import net.kencochrane.raven.connection.UdpConnection;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.LoggedEvent;
import net.kencochrane.raven.exception.InvalidDsnException;
import net.kencochrane.raven.marshaller.simplejson.JsonMarshaller;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Raven {
    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final String NAME = "Raven-Java/3.0";
    private static final Logger logger = Logger.getLogger(Raven.class.getCanonicalName());
    private Connection connection;

    public Raven() {
        this(dsnLookup());
    }

    public Raven(String dsn) {
        this(new Dsn(dsn));
    }

    public Raven(Dsn dsn) {
        this(determineConnection(dsn));
    }

    public Raven(Connection connection) {
        this.connection = connection;
    }

    private static String dsnLookup() {
        String dsn = null;

        // Try to obtain the DSN from JNDI
        try {
            Context c = new InitialContext();
            dsn = (String) c.lookup("java:comp/env/sentry/dsn");
        } catch (NoInitialContextException e) {
            logger.log(Level.INFO, "JNDI not configured for sentry (NoInitialContextEx)");
        } catch (NamingException e) {
            logger.log(Level.INFO, "No /sentry/dsn in JNDI");
        } catch (RuntimeException ex) {
            logger.log(Level.INFO, "Odd RuntimeException while testing for JNDI: " + ex.getMessage());
        }

        // Try to obtain the DSN from a System Environment Variable
        if (dsn == null)
            dsn = System.getenv(Dsn.DSN_VARIABLE);

        // Try to obtain the DSN from a Java System Property
        if (dsn == null)
            dsn = System.getProperty(Dsn.DSN_VARIABLE);

        if (dsn != null) {
            return dsn;
        } else {
            throw new InvalidDsnException("Couldn't find a Sentry DSN in either the Java or System environment.");
        }
    }

    private static Charset determineCharset(Dsn dsn) {
        String charset = DEFAULT_CHARSET;

        if (dsn.getOptions().containsKey(Dsn.CHARSET_OPTION))
            charset = dsn.getOptions().get(Dsn.CHARSET_OPTION);

        return Charset.forName(charset);
    }

    //TODO: Replace with a factory?
    private static Connection determineConnection(Dsn dsn) {
        String protocol = dsn.getProtocol();
        Connection connection = null;
        Charset charset = determineCharset(dsn);
        JsonMarshaller marshaller = new JsonMarshaller();
        marshaller.setCompression(!dsn.getOptions().containsKey(Dsn.NOCOMPRESSION_OPTION));
        marshaller.setCharset(charset);

        if (protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https")) {
            HttpConnection httpConnection = new HttpConnection(dsn);
            httpConnection.setMarshaller(marshaller);
            connection = httpConnection;
        } else if (protocol.equalsIgnoreCase("udp")) {
            UdpConnection udpConnection = new UdpConnection(dsn);
            udpConnection.setCharset(charset);
            udpConnection.setMarshaller(marshaller);
            connection = udpConnection;
        } else {
            logger.log(Level.WARNING,
                    "Couldn't figure out automatically a connection to Sentry, one should be set manually");
        }

        if (dsn.getOptions().containsKey(Dsn.ASYNC_OPTION))
            connection = new AsyncConnection(connection);

        return connection;
    }

    public UUID sendEvent(EventBuilder eventBuilder) {
        LoggedEvent event = eventBuilder.build();
        sendEvent(event);
        return event.getId();
    }

    public void sendEvent(LoggedEvent event) {
        try {
            connection.send(event);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An exception occurred while sending the event to Sentry.", e);
        }
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }
}