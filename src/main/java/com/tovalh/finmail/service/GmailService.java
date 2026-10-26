package com.tovalh.finmail.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.tovalh.finmail.parser.ParserCorreoBanco;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GmailService {

    private static final String APPLICATION_NAME = "FinMail";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIR = "tokens";
    private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY);

    private final String consultaRemitentes;
    private Gmail gmailClient;

    // La consulta sale de los remitentes de cada parser: un banco nuevo no requiere tocar esta clase.
    public GmailService(List<ParserCorreoBanco> parsers) {
        this.consultaRemitentes = parsers.stream()
                .flatMap(p -> p.remitentes().stream())
                .collect(Collectors.joining(" OR ", "from:(", ")"));
    }

    @PostConstruct
    public void init() throws Exception {
        this.gmailClient = buildGmailClient();
    }

    private Gmail buildGmailClient() throws Exception {
        InputStream in = GmailService.class
                .getResourceAsStream("/credentials.json");

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                clientSecrets,
                SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIR)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver
                .Builder()
                .setPort(8888)
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
                .authorize("user");

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    // Para el scheduler basta con los últimos 20
    public List<Message> fetchBankMessages() throws Exception {
        return fetchBankMessages(consultaRemitentes, 20L);
    }

    // Carga histórica: todo desde la fecha, de a 500 por página
    public List<Message> fetchAllBankMessagesSince(String afterDate) throws Exception {
        String query = consultaRemitentes + " after:" + afterDate;
        List<Message> all = new ArrayList<>();
        String pageToken = null;

        do {
            var request = gmailClient.users().messages()
                    .list("me")
                    .setQ(query)
                    .setMaxResults(500L);

            if (pageToken != null) request.setPageToken(pageToken);

            ListMessagesResponse response = request.execute();
            if (response.getMessages() == null) break;

            for (Message msg : response.getMessages()) {
                all.add(fetchFull(msg.getId()));
            }

            pageToken = response.getNextPageToken();
        } while (pageToken != null);

        return all;
    }

    private List<Message> fetchBankMessages(String query, long maxResults) throws Exception {
        ListMessagesResponse response = gmailClient.users()
                .messages()
                .list("me")
                .setQ(query)
                .setMaxResults(maxResults)
                .execute();

        if (response.getMessages() == null) return List.of();

        List<Message> result = new ArrayList<>();
        for (Message msg : response.getMessages()) {
            result.add(fetchFull(msg.getId()));
        }
        return result;
    }

    private Message fetchFull(String messageId) throws Exception {
        return gmailClient.users().messages()
                .get("me", messageId)
                .setFormat("full")
                .execute();
    }
}
