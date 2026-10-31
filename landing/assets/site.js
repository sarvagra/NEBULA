// Kept external rather than inline: the page is served behind a strict
// Content-Security-Policy (script-src 'self') from the Nebula backend.
document.getElementById('year').textContent = new Date().getFullYear();
