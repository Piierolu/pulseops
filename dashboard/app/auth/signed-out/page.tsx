export default function SignedOut() {
  return (
    <main className="authError signedOut">
      <p className="sectionCode">IDENTITY / SESSION CLOSED</p>
      <h1>SIGNED OUT</h1>
      <p>Your local PulseOps session has been removed. The identity provider session was not changed.</p>
      <a href="/api/auth/login?returnTo=/">SIGN IN AGAIN</a>
    </main>
  );
}
