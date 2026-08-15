export default function AuthenticationError() {
  return (
    <main className="authError">
      <p className="sectionCode">IDENTITY / OIDC</p>
      <h1>AUTHENTICATION FAILED</h1>
      <p>The identity provider did not complete a valid PulseOps session.</p>
      <a href="/api/auth/login?returnTo=/">TRY AGAIN</a>
    </main>
  );
}
