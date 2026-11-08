package com.tono.launcher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Bot\u00f3n de la barra superior. action: "settings", "customize" o "app:<paquete>" */
data class TopButton(val label: String, val action: String)

data class AppRow(val packages: List<String>)

data class Config(
    val userName: String,
    val wallpaper: String,
    val topButtons: List<TopButton>,
    val rows: List<AppRow>,
    val wallMode: Int = 0,
    val wallZoom: Int = 100,
    val wallDim: Int = 40
)

object ConfigStore {
    private const val PREFS = "launcher"
    private const val KEY = "config"

    fun load(ctx: Context): Config? {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(s)
            val tb = o.getJSONArray("top")
            val rw = o.getJSONArray("rows")
            var top = (0 until tb.length()).map { i ->
                val t = tb.getJSONObject(i)
                TopButton(t.getString("l"), t.getString("a"))
            }
            if (top.none { it.action == "customize" }) {
                top = listOf(TopButton("Personalizar", "customize")) + top
            }
            if (o.optInt("v", 1) < 2 && top.none { it.action == "hdmi" }) {
                top = top + TopButton("HDMI", "hdmi")
            }
            val rows = (0 until rw.length()).map { i ->
                val r = rw.getJSONArray(i)
                AppRow((0 until r.length()).map { k -> r.getString(k) })
            }
            Config(o.optString("name", ""), o.optString("wall", ""), top, rows.ifEmpty { listOf(AppRow(emptyList())) },
                o.optInt("wm", 0), o.optInt("wz", 100), o.optInt("wd", 40))
        } catch (e: Exception) {
            null
        }
    }

    fun save(ctx: Context, c: Config) {
        val o = JSONObject()
        o.put("name", c.userName)
        o.put("wall", c.wallpaper)
        o.put("wm", c.wallMode)
        o.put("wz", c.wallZoom)
        o.put("wd", c.wallDim)
        o.put("v", 2)
        val tb = JSONArray()
        c.topButtons.forEach { tb.put(JSONObject().put("l", it.label).put("a", it.action)) }
        o.put("top", tb)
        val rw = JSONArray()
        c.rows.forEach { r ->
            val a = JSONArray()
            r.packages.forEach { a.put(it) }
            rw.put(a)
        }
        o.put("rows", rw)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }

    fun default(ctx: Context): Config {
        val installed = Apps.launchable(ctx).map { it.pkg }
        val favorites = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.wbd.stream",
            "com.hbo.hbonow",
            "com.android.vending"
        ).filter { it in installed }
        val rest = installed.filter { it !in favorites }.take(20)
        return Config(
            userName = "Sebas",
            wallpaper = "",
            topButtons = listOf(TopButton("Personalizar", "customize"), TopButton("Ajustes", "settings"), TopButton("HDMI", "hdmi")),
            rows = listOf(AppRow(favorites), AppRow(rest))
        )
    }
}

object Cfg {
    private fun Config.setRow(r: Int, l: List<String>): Config =
        copy(rows = rows.toMutableList().also { it[r] = AppRow(l) })

    /** Mueve la app i de la fila r hacia la izquierda (-1) o derecha (+1), saltando apps desinstaladas. */
    fun move(c: Config, r: Int, i: Int, d: Int, visible: Set<String>): Config {
        val list = c.rows[r].packages.toMutableList()
        var j = i + d
        while (j in list.indices && list[j] !in visible) j += d
        if (j !in list.indices) return c
        Collections.swap(list, i, j)
        return c.setRow(r, list)
    }

    fun moveToRow(c: Config, r: Int, i: Int, dr: Int): Config {
        val t = r + dr
        if (t !in c.rows.indices) return c
        val pkg = c.rows[r].packages[i]
        val from = c.rows[r].packages.filterIndexed { k, _ -> k != i }
        val to = c.rows[t].packages.filter { it != pkg } + pkg
        return c.setRow(r, from).setRow(t, to)
    }

    fun remove(c: Config, r: Int, i: Int): Config =
        c.setRow(r, c.rows[r].packages.filterIndexed { k, _ -> k != i })

    fun add(c: Config, r: Int, pkg: String): Config =
        if (pkg in c.rows[r].packages) c else c.setRow(r, c.rows[r].packages + pkg)
}

data class AppInfo(val pkg: String, val label: String)

class TileIcon(val bmp: ImageBitmap, val banner: Boolean)

object Apps {
    fun launchable(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val result = LinkedHashMap<String, AppInfo>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(i, 0)) {
                val p = ri.activityInfo.packageName
                if (p == ctx.packageName || result.containsKey(p)) continue
                result[p] = AppInfo(p, ri.loadLabel(pm).toString())
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private val cache = HashMap<String, TileIcon>()

    fun icon(ctx: Context, pkg: String): TileIcon? {
        cache[pkg]?.let { return it }
        val loaded = loadIcon(ctx, pkg)
        if (loaded != null) cache[pkg] = loaded
        return loaded
    }

    private fun loadIcon(ctx: Context, pkg: String): TileIcon? {
        val pm = ctx.packageManager
        return try {
            val lean = pm.getLeanbackLaunchIntentForPackage(pkg)
            val banner: Drawable? =
                (if (lean != null) pm.getActivityBanner(lean) else null) ?: pm.getApplicationBanner(pkg)
            if (banner != null) {
                TileIcon(banner.toBitmap(320, 180).asImageBitmap(), true)
            } else {
                TileIcon(pm.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap(), false)
            }
        } catch (e: Exception) {
            null
        }
    }
}

object Wallpapers {
    fun dir(ctx: Context): File? = ctx.getExternalFilesDir("wallpapers")

    fun list(ctx: Context): List<String> {
        val ok = setOf("jpg", "jpeg", "png", "webp")
        return dir(ctx)?.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in ok }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    fun load(ctx: Context, name: String): ImageBitmap? {
        if (name.isBlank()) return null
        return try {
            val d = dir(ctx) ?: return null
            val f = File(d, name)
            if (!f.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var s = 1
            while (bounds.outWidth / (s * 2) >= 3840) s *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = s }
            BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherApp(tick.intValue) }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue = tick.intValue + 1
    }
}

sealed class Screen {
    object Home : Screen()
    data class TileMenu(val r: Int, val i: Int) : Screen()
    data class AddApp(val r: Int) : Screen()
    object Customize : Screen()
    object Wallpapers : Screen()
    object WallSettings : Screen()
    object Hdmi : Screen()
    object DeleteRow : Screen()
    object TopButtons : Screen()
    object AddTopApp : Screen()
    object EditName : Screen()
}

class MI(val text: String, val onClick: () -> Unit)

@Composable
fun LauncherApp(tick: Int) {
    val ctx = LocalContext.current
    var config by remember {
        mutableStateOf(ConfigStore.load(ctx) ?: ConfigStore.default(ctx).also { ConfigStore.save(ctx, it) })
    }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val installed = remember(tick) { Apps.launchable(ctx) }
    val installedSet = remember(installed) { installed.map { it.pkg }.toSet() }
    var wall by remember { mutableStateOf<ImageBitmap?>(null) }
    var now by remember { mutableStateOf(Date()) }
    val firstFocus = remember { FocusRequester() }

    fun update(c: Config) {
        config = c
        ConfigStore.save(ctx, c)
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun runTop(b: TopButton) {
        when {
            b.action == "settings" -> try {
                ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                toast("No se pudo abrir Ajustes")
            }
            b.action == "customize" -> screen = Screen.Customize
            b.action == "hdmi" -> {
                val inputs = HdmiInputs.list(ctx)
                when {
                    inputs.isEmpty() -> toast("No se encontraron entradas HDMI")
                    inputs.size == 1 -> if (!HdmiInputs.open(ctx, inputs[0])) toast("No se pudo abrir la entrada HDMI")
                    else -> screen = Screen.Hdmi
                }
            }
            b.action.startsWith("app:") ->
                if (!Apps.launch(ctx, b.action.removePrefix("app:"))) toast("No se pudo abrir ${b.label}")
        }
    }

    LaunchedEffect(config.wallpaper, tick) {
        wall = withContext(Dispatchers.IO) { Wallpapers.load(ctx, config.wallpaper) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    LaunchedEffect(screen == Screen.Home) {
        if (screen == Screen.Home) {
            delay(200)
            runCatching { firstFocus.requestFocus() }
        }
    }

    BackHandler(true) { screen = Screen.Home }

    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val greet = when {
        hour < 12 -> "BUENOS D\u00cdAS"
        hour < 19 -> "BUENAS TARDES"
        else -> "BUENAS NOCHES"
    }
    val greeting = if (config.userName.isBlank()) greet else "$greet, ${config.userName.uppercase()}"
    val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val dateText = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "PE")).format(now).uppercase()

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0E1420), Color(0xFF1B2838))))
        ) {
            wall?.let {
                Image(
                    it,
                    null,
                    Modifier
                        .fillMaxSize()
                        .scale(config.wallZoom / 100f),
                    contentScale = if (config.wallMode == 1) ContentScale.Fit else ContentScale.Crop,
                    filterQuality = FilterQuality.High
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = config.wallDim / 100f * 0.5f),
                                Color.Black.copy(alpha = (config.wallDim / 100f * 1.4f).coerceAtMost(0.95f))
                            )
                        )
                    )
            )

            Column(Modifier.fillMaxSize()) {
                // Encabezado
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 45.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(greeting, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(timeText, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
                        Text(dateText, color = Color.White, fontSize = 15.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        config.topButtons.forEach { b -> Pill(b.label, b.action == "hdmi") { runTop(b) } }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Filas de apps
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val tileW = (maxWidth - 90.dp - 40.dp - 98.dp) / 8
                    val tileH = tileW * 9f / 16f
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 45.dp)
                    ) {
                        itemsIndexed(config.rows) { r, row ->
                            val vis = row.packages.withIndex().filter { it.value in installedSet }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (r == 0) Color(0x99252D3F) else Color.Transparent)
                            ) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(vis, key = { it.value }) { iv ->
                                        AppTile(
                                            pkg = iv.value,
                                            w = tileW,
                                            h = tileH,
                                            focusRequester = if (r == 0 && iv.index == vis.first().index) firstFocus else null,
                                            onOpen = {
                                                if (!Apps.launch(ctx, iv.value)) toast("No se pudo abrir la app")
                                            },
                                            onMenu = { screen = Screen.TileMenu(r, iv.index) }
                                        )
                                    }
                                    item {
                                        AddTile(
                                            w = tileW,
                                            h = tileH,
                                            focusRequester = if (r == 0 && vis.isEmpty()) firstFocus else null,
                                            onClick = { screen = Screen.AddApp(r) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Men\u00fas y di\u00e1logos
        val close = { screen = Screen.Home }
        when (val s = screen) {
            Screen.Home -> {}

            is Screen.TileMenu -> {
                val pkg = config.rows.getOrNull(s.r)?.packages?.getOrNull(s.i)
                if (pkg != null) {
                    val items = buildList {
                        add(MI("\u25c0  Mover a la izquierda") { update(Cfg.move(config, s.r, s.i, -1, installedSet)); close() })
                        add(MI("\u25b6  Mover a la derecha") { update(Cfg.move(config, s.r, s.i, 1, installedSet)); close() })
                        if (s.r > 0) add(MI("\u25b2  Subir a la fila de arriba") { update(Cfg.moveToRow(config, s.r, s.i, -1)); close() })
                        if (s.r < config.rows.size - 1) add(MI("\u25bc  Bajar a la fila de abajo") { update(Cfg.moveToRow(config, s.r, s.i, 1)); close() })
                        add(MI("\u2715  Ocultar (quitar de la pantalla)") { update(Cfg.remove(config, s.r, s.i)); close() })
                        add(MI("Cancelar") { close() })
                    }
                    MenuDialog(Apps.label(ctx, pkg), items, close)
                }
            }

            is Screen.AddApp -> {
                val current = config.rows.getOrNull(s.r)?.packages ?: emptyList()
                val items = installed.map { a ->
                    MI((if (a.pkg in current) "\u2713  " else "") + a.label) {
                        update(Cfg.add(config, s.r, a.pkg)); close()
                    }
                } + MI("Cancelar") { close() }
                MenuDialog("Agregar app a la fila ${s.r + 1}", items, close)
            }

            Screen.Customize -> {
                val items = buildList {
                    add(MI("Cambiar fondo de pantalla") { screen = Screen.Wallpapers })
                    add(MI("Ajustes del fondo (zoom y brillo)") { screen = Screen.WallSettings })
                    add(MI("Agregar una fila nueva") {
                        update(config.copy(rows = config.rows + AppRow(emptyList()))); close()
                    })
                    if (config.rows.size > 1) add(MI("Eliminar una fila") { screen = Screen.DeleteRow })
                    add(MI("Editar botones superiores") { screen = Screen.TopButtons })
                    add(MI("Cambiar mi nombre") { screen = Screen.EditName })
                    add(MI("Restaurar configuraci\u00f3n inicial") { update(ConfigStore.default(ctx)); close() })
                    add(MI("Cerrar") { close() })
                }
                MenuDialog(
                    "Personalizar",
                    items,
                    close,
                    message = "Tip: mant\u00e9n presionado OK sobre una app (o la tecla Men\u00fa) para moverla u ocultarla."
                )
            }

            Screen.Wallpapers -> {
                val files = Wallpapers.list(ctx)
                val items = buildList {
                    add(MI("Sin imagen (degradado)") { update(config.copy(wallpaper = "")); close() })
                    files.forEach { f -> add(MI(f) { update(config.copy(wallpaper = f)); close() }) }
                    add(MI("Cancelar") { close() })
                }
                MenuDialog(
                    "Fondo de pantalla",
                    items,
                    close,
                    message = "Copia im\u00e1genes JPG/PNG desde tu PC con ADB a:\n" +
                        (Wallpapers.dir(ctx)?.path ?: "(carpeta no disponible)")
                )
            }

            Screen.WallSettings -> {
                val items = buildList {
                    add(MI("Modo: " + (if (config.wallMode == 0) "Rellenar pantalla" else "Mostrar completa") + "  (cambiar)") {
                        update(config.copy(wallMode = 1 - config.wallMode))
                    })
                    add(MI("Alejar fondo  (-5%)") { update(config.copy(wallZoom = (config.wallZoom - 5).coerceIn(60, 130))) })
                    add(MI("Acercar fondo  (+5%)") { update(config.copy(wallZoom = (config.wallZoom + 5).coerceIn(60, 130))) })
                    add(MI("Oscurecer m\u00e1s  (+10%)") { update(config.copy(wallDim = (config.wallDim + 10).coerceIn(0, 90))) })
                    add(MI("Oscurecer menos  (-10%)") { update(config.copy(wallDim = (config.wallDim - 10).coerceIn(0, 90))) })
                    add(MI("Restablecer fondo") { update(config.copy(wallMode = 0, wallZoom = 100, wallDim = 40)) })
                    add(MI("Listo") { close() })
                }
                MenuDialog(
                    "Ajustes del fondo",
                    items,
                    close,
                    message = "Zoom: ${config.wallZoom}%    Oscuridad: ${config.wallDim}%"
                )
            }

            Screen.Hdmi -> {
                val inputs = HdmiInputs.list(ctx)
                val items = inputs.map { info ->
                    MI(HdmiInputs.label(ctx, info)) {
                        if (!HdmiInputs.open(ctx, info)) toast("No se pudo abrir la entrada HDMI")
                        close()
                    }
                } + MI("Cancelar") { close() }
                MenuDialog("Entradas HDMI", items, close)
            }

            Screen.DeleteRow -> {
                val items = config.rows.mapIndexed { i, row ->
                    MI("Fila ${i + 1}  (${row.packages.size} apps)") {
                        update(config.copy(rows = config.rows.filterIndexed { k, _ -> k != i })); close()
                    }
                } + MI("Cancelar") { close() }
                MenuDialog("\u00bfQu\u00e9 fila eliminar?", items, close)
            }

            Screen.TopButtons -> {
                val items = buildList {
                    config.topButtons.filter { it.action != "customize" }.forEach { b ->
                        add(MI("Quitar: ${b.label}") {
                            update(config.copy(topButtons = config.topButtons.filter { it != b })); close()
                        })
                    }
                    if (config.topButtons.none { it.action == "settings" }) {
                        add(MI("Agregar: Ajustes del sistema") {
                            update(config.copy(topButtons = config.topButtons + TopButton("Ajustes", "settings"))); close()
                        })
                    }
                    if (config.topButtons.none { it.action == "hdmi" }) {
                        add(MI("Agregar: HDMI") {
                            update(config.copy(topButtons = config.topButtons + TopButton("HDMI", "hdmi"))); close()
                        })
                    }
                    add(MI("Agregar: abrir una app\u2026") { screen = Screen.AddTopApp })
                    add(MI("Listo") { close() })
                }
                MenuDialog("Botones superiores", items, close)
            }

            Screen.AddTopApp -> {
                val items = installed.map { a ->
                    MI(a.label) {
                        update(config.copy(topButtons = config.topButtons + TopButton(a.label, "app:" + a.pkg))); close()
                    }
                } + MI("Cancelar") { close() }
                MenuDialog("Elige la app del bot\u00f3n", items, close)
            }

            Screen.EditName -> TextDialog("Tu nombre en el saludo", config.userName, close) {
                update(config.copy(userName = it)); close()
            }
        }
    }
}

/** Click con control remoto: OK = abrir, OK mantenido o tecla Men\u00fa = onLongClick. */
@Composable
fun Modifier.tvClickable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    var longFired by remember { mutableStateOf(false) }
    var downSeen by remember { mutableStateOf(false) }
    return this
        .onKeyEvent { ev ->
            val k = ev.key
            if (k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter) {
                when (ev.type) {
                    KeyEventType.KeyDown -> {
                        if (ev.nativeKeyEvent.repeatCount == 0) {
                            downSeen = true
                            longFired = false
                        } else if (!longFired && onLongClick != null) {
                            longFired = true
                            onLongClick()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (downSeen && !longFired) onClick()
                        downSeen = false
                        longFired = false
                        true
                    }
                    else -> false
                }
            } else if (k == Key.Menu && ev.type == KeyEventType.KeyDown && onLongClick != null) {
                onLongClick()
                true
            } else {
                false
            }
        }
        .focusable()
}

@Composable
fun AppTile(
    pkg: String,
    w: Dp,
    h: Dp,
    focusRequester: FocusRequester?,
    onOpen: () -> Unit,
    onMenu: () -> Unit
) {
    val ctx = LocalContext.current
    val icon = remember(pkg) { Apps.icon(ctx, pkg) }
    val label = remember(pkg) { Apps.label(ctx, pkg) }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "tileScale")
    Column(Modifier.width(w), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(w, h)
                .scale(scale)
                .border(if (focused) 3.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E2530))
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .tvClickable(onOpen, onMenu),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                if (icon.banner) {
                    Image(icon.bmp, label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Image(icon.bmp, label, Modifier.size(h * 0.6f), contentScale = ContentScale.Fit)
                }
            } else {
                Text(label, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 4.dp)
                .height(18.dp)
                .alpha(if (focused) 1f else 0f)
        )
    }
}

@Composable
fun AddTile(w: Dp, h: Dp, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(w)) {
        Box(
            Modifier
                .size(w, h)
                .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x66FFFFFF), RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(if (focused) Color(0x55FFFFFF) else Color(0x22FFFFFF))
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .tvClickable(onClick),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
fun Pill(text: String, hdmi: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) Color.Black else Color.White
    Box(
        Modifier
            .width(104.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color(0x38FFFFFF))
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hdmi) {
                Canvas(Modifier.size(14.dp, 11.dp)) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height * 0.55f)
                        lineTo(size.width * 0.8f, size.height)
                        lineTo(size.width * 0.2f, size.height)
                        lineTo(0f, size.height * 0.55f)
                        close()
                    }
                    drawPath(path, fg, style = Stroke(width = 2f))
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                color = fg,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MenuButton(text: String, fr: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White else Color(0x33FFFFFF))
            .then(if (fr != null) Modifier.focusRequester(fr) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MenuDialog(title: String, items: List<MI>, onDismiss: () -> Unit, message: String? = null) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { fr.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF2182030))
                .padding(24.dp)
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (message != null) {
                Text(message, color = Color(0xFFB0B8C8), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(items) { i, mi ->
                    MenuButton(mi.text, if (i == 0) fr else null, mi.onClick)
                }
            }
        }
    }
}

@Composable
fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { fr.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF2182030))
                .padding(24.dp)
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fr)
            )
            Spacer(Modifier.height(14.dp))
            MenuButton("Guardar") { onSave(text.trim()) }
            MenuButton("Cancelar", null, onDismiss)
        }
    }
}


object HdmiInputs {
    fun list(ctx: Context): List<TvInputInfo> = try {
        val tm = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        tm.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI }
    } catch (e: Exception) {
        emptyList()
    }

    fun label(ctx: Context, info: TvInputInfo): String =
        (info.loadCustomLabel(ctx) ?: info.loadLabel(ctx) ?: info.id).toString()

    fun open(ctx: Context, info: TvInputInfo): Boolean = try {
        val uri = TvContract.buildChannelUriForPassthroughInput(info.id)
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
