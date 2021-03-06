/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 *
 */

package io.javalin

import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.testing.TestUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.URLEncoder

class TestRouting {

    private val okHttp = OkHttpClient().newBuilder().build()
    fun OkHttpClient.getBody(path: String) = this.newCall(Request.Builder().url(path).get().build()).execute().body()!!.string()

    @Test
    fun `wildcard first works`() = TestUtil.test { app, http ->
        app.get("/*/test") { it.result("!") }
        assertThat(http.getBody("/en/test")).isEqualTo("!")
    }

    @Test
    fun `wildcard middle works`() = TestUtil.test { app, http ->
        app.get("/test/*/test") { it.result("!") }
        assertThat(http.getBody("/test/en/test")).isEqualTo("!")
    }

    @Test
    fun `wildcard end works`() = TestUtil.test { app, http ->
        app.get("/test/*") { it.result("!") }
        assertThat(http.getBody("/test/en")).isEqualTo("!")
    }

    @Test
    fun `case sensitive urls work`() = TestUtil.test { app, http ->
        app.get("/My-Url") { ctx -> ctx.result("OK") }
        assertThat(http.getBody("/MY-URL")).isEqualTo("Not found")
        assertThat(http.getBody("/My-Url")).isEqualTo("OK")
    }

    @Test
    fun `utf-8 encoded path-params work`() = TestUtil.test { app, http ->
        app.get("/:path-param") { ctx -> ctx.result(ctx.pathParam("path-param")) }
        assertThat(okHttp.getBody(http.origin + "/" + URLEncoder.encode("TE/ST", "UTF-8"))).isEqualTo("TE/ST")
    }

    @Test
    fun `path-params work case-sensitive`() = TestUtil.test { app, http ->
        app.get("/:userId") { ctx -> ctx.result(ctx.pathParam("userId")) }
        assertThat(http.getBody("/path-param")).isEqualTo("path-param")
        app.get("/:a/:A") { ctx -> ctx.result("${ctx.pathParam("a")}-${ctx.pathParam("A")}") }
        assertThat(http.getBody("/a/B")).isEqualTo("a-B")
    }

    @Test
    fun `path-param values retain their casing`() = TestUtil.test { app, http ->
        app.get("/:path-param") { ctx -> ctx.result(ctx.pathParam("path-param")) }
        assertThat(http.getBody("/SomeCamelCasedValue")).isEqualTo("SomeCamelCasedValue")
    }

    @Test
    fun `path regex works`() = TestUtil.test { app, http ->
        app.get("/:path-param/[0-9]+/") { ctx -> ctx.result(ctx.pathParam("path-param")) }
        assertThat(http.getBody("/test/pathParam")).isEqualTo("Not found")
        assertThat(http.getBody("/test/21")).isEqualTo("test")
    }

    @Test
    fun `automatic slash prefixing works`() = TestUtil.test { app, http ->
        app.routes {
            path("test") {
                path(":id") { get { ctx -> ctx.result(ctx.pathParam("id")) } }
                get { ctx -> ctx.result("test") }
            }
        }
        assertThat(http.getBody("/test/path-param/")).isEqualTo("path-param")
        assertThat(http.getBody("/test/")).isEqualTo("test")
    }

    @Test
    fun `non sub-path wildcard works for paths`() = TestUtil.test { app, http ->
        app.get("/p") { it.result("GET") }
        app.get("/p/test") { it.result("GET") }
        assertThat(http.getBody("/p")).isEqualTo("GET")
        assertThat(http.getBody("/p/test")).isEqualTo("GET")
        app.after("/p*") { it.result((it.resultString() ?: "") + "AFTER") }
        assertThat(http.getBody("/p")).isEqualTo("GETAFTER")
        assertThat(http.getBody("/p/test")).isEqualTo("GETAFTER")
    }

    @Test
    fun `non sub-path wildcard works for path-params`() = TestUtil.test { app, http ->
        app.get("/:pp") { it.result(it.resultString() + it.pathParam("pp")) }
        app.get("/:pp/test") { it.result(it.resultString() + it.pathParam("pp")) }
        assertThat(http.getBody("/123")).isEqualTo("null123")
        assertThat(http.getBody("/123/test")).isEqualTo("null123")
        app.before("/:pp*") { it.result("BEFORE") }
        assertThat(http.getBody("/123")).isEqualTo("BEFORE123")
        assertThat(http.getBody("/123/test")).isEqualTo("BEFORE123")
    }

    @Test
    fun `extracting path-param and splat works`() = TestUtil.test { app, http ->
        app.get("/path/:path-param/*") { ctx -> ctx.result("/" + ctx.pathParam("path-param") + "/" + ctx.splat(0)) }
        assertThat(http.getBody("/path/P/S")).isEqualTo("/P/S")
    }

    @Test
    fun `utf-8 encoded splat works`() = TestUtil.test { app, http ->
        app.get("/:path-param/path/*") { ctx -> ctx.result(ctx.pathParam("path-param") + ctx.splat(0)!!) }
        val responseBody = okHttp.getBody(http.origin + "/"
                + URLEncoder.encode("java/kotlin", "UTF-8")
                + "/path/"
                + URLEncoder.encode("/java/kotlin", "UTF-8")
        )
        assertThat(responseBody).isEqualTo("java/kotlin/java/kotlin")
    }

    @Test
    fun `getting splat-list works`() = TestUtil.test { app, http ->
        app.get("/*/*/*") { ctx -> ctx.result(ctx.splats().toString()) }
        assertThat(http.getBody("/1/2/3")).isEqualTo("[1, 2, 3]")
    }
}
