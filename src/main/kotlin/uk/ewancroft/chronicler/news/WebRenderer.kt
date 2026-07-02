package uk.ewancroft.chronicler.news

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import uk.ewancroft.chronicler.config.WebConfig
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

class WebRenderer(
    private val webConfig: WebConfig,
    private val newspaperConfig: uk.ewancroft.chronicler.config.NewspaperConfig,
    private val webDir: Path,
) {

    private var server: HttpServer? = null
    private var latestHtml: String = "<html><body><h1>No newspaper published yet.</h1></body></html>"

    fun renderAndServe(newspaper: Newspaper) {
        val html = renderHtml(newspaper)
        latestHtml = html

        if (webConfig.writeFiles) {
            try {
                Files.createDirectories(webDir)
                webDir.resolve("index.html").toFile().writeText(html, StandardCharsets.UTF_8)
            } catch (_: Exception) {
            }
        }

        if (server == null && webConfig.port > 0) {
            startServer()
        }
    }

    private fun renderHtml(newspaper: Newspaper): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm")
        val fromStr = dateFmt.format(Date(newspaper.fromTime))
        val toStr = dateFmt.format(Date(newspaper.toTime))

        val sectionsHtml = newspaper.sections.joinToString("\n") { section ->
            val storiesHtml = section.stories.joinToString("\n") { story ->
                val playersHtml = if (story.players.isNotEmpty()) {
                    "<p class=\"players\">— ${story.players.joinToString(", ")}</p>"
                } else ""

                """
                <div class="story">
                    <h3>${escapeHtml(story.headline)}</h3>
                    <p>${escapeHtml(story.body)}</p>
                    $playersHtml
                </div>
                """
            }

            """
            <section>
                <h2>${escapeHtml(section.title)}</h2>
                $storiesHtml
            </section>
            """
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${escapeHtml(newspaperConfig.title)} #${newspaper.issueNumber}</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Georgia', 'Times New Roman', serif;
            background: #1a1a2e;
            color: #e0e0e0;
            line-height: 1.6;
            max-width: 800px;
            margin: 0 auto;
            padding: 2rem;
        }
        h1 {
            color: #ffaa00;
            font-size: 2.5rem;
            text-align: center;
            border-bottom: 2px solid #ffaa00;
            padding-bottom: 0.5rem;
            margin-bottom: 0.5rem;
        }
        .issue-info {
            text-align: center;
            color: #888;
            margin-bottom: 2rem;
            font-size: 0.9rem;
        }
        section {
            margin-bottom: 2rem;
            padding: 1rem;
            background: #16213e;
            border-radius: 8px;
        }
        h2 {
            color: #ffaa00;
            font-size: 1.5rem;
            margin-bottom: 1rem;
            border-bottom: 1px solid #333;
            padding-bottom: 0.3rem;
        }
        .story {
            margin-bottom: 1rem;
            padding: 0.8rem;
            background: #0f3460;
            border-radius: 6px;
        }
        .story h3 {
            color: #ffffff;
            font-size: 1.1rem;
            margin-bottom: 0.3rem;
        }
        .story p {
            color: #ccc;
            font-size: 0.95rem;
        }
        .story .players {
            color: #888;
            font-size: 0.8rem;
            margin-top: 0.3rem;
        }
        .footer {
            text-align: center;
            color: #555;
            font-size: 0.8rem;
            margin-top: 3rem;
            padding-top: 1rem;
            border-top: 1px solid #333;
        }
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
            gap: 0.5rem;
        }
        .stat-card {
            background: #0f3460;
            padding: 0.8rem;
            border-radius: 6px;
            text-align: center;
        }
        .stat-card .value {
            font-size: 1.5rem;
            color: #ffaa00;
            font-weight: bold;
        }
        .stat-card .label {
            font-size: 0.85rem;
            color: #aaa;
        }
    </style>
</head>
<body>
    <h1>${escapeHtml(newspaperConfig.title)}</h1>
    <div class="issue-info">
        Issue #${newspaper.issueNumber} &mdash;
        $fromStr to $toStr
    </div>
    $sectionsHtml
    <div class="footer">
        Generated by Chronicler &mdash; ${toStr}
    </div>
</body>
</html>
        """.trimIndent()
    }

    private fun startServer() {
        try {
            val addr = InetSocketAddress(webConfig.port)
            server = HttpServer.create(addr, 0).also { srv ->
                srv.createContext("/", this::handleRequest)
                srv.executor = Executors.newSingleThreadExecutor()
                srv.start()
            }
        } catch (_: Exception) {
        }
    }

    private fun handleRequest(exchange: HttpExchange) {
        val bytes = latestHtml.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { body: OutputStream ->
            body.write(bytes)
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
