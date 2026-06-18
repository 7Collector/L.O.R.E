import os
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from abc import ABC, abstractmethod

class EmailProvider(ABC):
    @abstractmethod
    def send_magic_link(self, to_email: str, link: str) -> bool:
        pass

class MockEmailProvider(EmailProvider):
    def send_magic_link(self, to_email: str, link: str) -> bool:
        print("\n" + "="*80)
        print(f"MOCK EMAIL TO: {to_email}")
        print(f"MAGIC LINK: {link}")
        print("="*80 + "\n")
        return True

class SMTPEmailProvider(EmailProvider):
    def __init__(self, host: str, port: int, user: str, password: str, from_email: str):
        self.host = host
        self.port = port
        self.user = user
        self.password = password
        self.from_email = from_email

    def send_magic_link(self, to_email: str, link: str) -> bool:
        try:
            msg = MIMEMultipart()
            msg["From"] = self.from_email
            msg["To"] = to_email
            msg["Subject"] = "Your L.O.R.E Access Link"

            body = f"""
            Hello,

            You requested an access link to sign in to L.O.R.E.
            Click the link below to verify your session:

            {link}

            This link will expire shortly. If you did not request this, please ignore this email.
            """
            msg.attach(MIMEText(body, "plain"))

            # Using TLS
            server = smtplib.SMTP(self.host, self.port)
            server.starttls()
            server.login(self.user, self.password)
            server.sendmail(self.from_email, to_email, msg.as_string())
            server.quit()
            return True
        except Exception as e:
            print(f"Failed to send SMTP email to {to_email}: {e}")
            return False

def get_email_provider() -> EmailProvider:
    host = os.getenv("SMTP_HOST", "mock")
    if host.lower() == "mock" or not host:
        return MockEmailProvider()
    
    port = int(os.getenv("SMTP_PORT", "587"))
    user = os.getenv("SMTP_USER", "")
    password = os.getenv("SMTP_PASSWORD", "")
    from_email = os.getenv("SMTP_FROM_EMAIL", "noreply@lore.local")
    return SMTPEmailProvider(host, port, user, password, from_email)
