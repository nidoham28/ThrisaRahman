package com.naira.assistant.core.tools.executor

import com.naira.assistant.domain.settings.EmailContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationToolExecutorTest {

    private val contacts = listOf(
        EmailContact(name = "Alex Morgan", email = "alex@example.com"),
        EmailContact(name = "Alex Rivera", email = "rivera@example.com"),
    )

    @Test
    fun `recipient names resolve and repeated addresses are included once`() {
        val result = resolveEmailRecipients(
            listOf("Alex Morgan", "alex@example.com", "ALEX@EXAMPLE.COM"),
            contacts,
        )

        assertEquals(
            EmailRecipientResolution.Resolved(listOf("alex@example.com")),
            result,
        )
    }

    @Test
    fun `ambiguous recipient name asks the assistant to look up the contact`() {
        val result = resolveEmailRecipients(listOf("Alex"), contacts)

        assertTrue(result is EmailRecipientResolution.Failed)
        assertTrue((result as EmailRecipientResolution.Failed).message.contains("find_email_contact"))
    }

    @Test
    fun `unknown name is not treated as an email address`() {
        val result = resolveEmailRecipients(listOf("Taylor"), contacts)

        assertTrue(result is EmailRecipientResolution.Failed)
    }
}
