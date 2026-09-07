package app.inkloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.inkloom.document.LiquifyMode
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.model.BrushToolRole
import app.inkloom.model.Compatibility
import app.inkloom.model.SystemBrushCatalog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushToolBindingPersistenceTest {
    private fun withStore(test: (BrushMetadataStore, android.content.SharedPreferences) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "tool-binding-test-${System.nanoTime()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try { test(BrushMetadataStore(context, name, "brushes"), prefs) }
        finally { prefs.edit().clear().commit() }
    }

    @Test fun renamedFactoryToolsKeepTheirDeclaredRolesAndLiquifyModesAfterReload() = withStore { store, _ ->
        val tools = SystemBrushCatalog.brushes.map { it.copy(name = "Eraser Blend Liquify", groupName = "Erasers", tags = listOf("push-right")) }
        store.save(tools)
        val loaded = store.load()
        assertEquals(tools.size, loaded.size)
        tools.zip(loaded).forEach { (before, after) -> assertEquals(before.toolBinding, after.toolBinding) }
        assertEquals(LiquifyMode.TWIRL_COUNTERCLOCKWISE, loaded.first { it.id == "system-liquify-twirl-left" }.toolBinding.liquifyMode)
    }

    @Test fun oldExplicitEraserAndFactoryHistoryMigrateWithoutUsingDisplayNames() = withStore { store, prefs ->
        val eraser = SystemBrushCatalog.byId("system-hard-eraser")!!
        val liquify = SystemBrushCatalog.byId("system-liquify-twirl-left")!!
        val paint = SystemBrushCatalog.byId("system-g-pen")!!.copy(name = "Eraser", groupName = "Erasers")
        store.save(listOf(eraser, liquify, paint))
        val array = JSONArray(prefs.getString("brushes", null))
        for (index in 0 until array.length()) array.getJSONObject(index).remove("toolBinding")
        array.getJSONObject(0).put("isEraser", true)
        array.getJSONObject(1).put("id", "system-liquify-twirl-left::inkloom-version::42-0")
        prefs.edit().putString("brushes", array.toString()).commit()
        val loaded = store.load()
        assertEquals(3, loaded.size)
        assertEquals(BrushToolRole.ERASE, loaded[0].toolBinding.role)
        assertEquals(LiquifyMode.TWIRL_COUNTERCLOCKWISE, loaded[1].toolBinding.liquifyMode)
        assertEquals(BrushToolRole.PAINT, loaded[2].toolBinding.role)
        store.save(loaded)
        val normalized = JSONArray(prefs.getString("brushes", null))
        assertFalse(normalized.getJSONObject(0).has("isEraser"))
        assertTrue(normalized.getJSONObject(0).has("toolBinding"))
    }

    @Test fun unknownRolePreservesItsRecordAndReasonWithoutAnExecutablePayload() = withStore { store, prefs ->
        store.save(listOf(SystemBrushCatalog.byId("system-g-pen")!!, SystemBrushCatalog.byId("system-hb-pencil")!!))
        val array = JSONArray(prefs.getString("brushes", null))
        array.getJSONObject(0).put("toolBinding", JSONObject().put("role", "UNRECOGNIZED_ROLE"))
        prefs.edit().putString("brushes", array.toString()).commit()
        val loaded = store.load()
        assertEquals(2, loaded.size)
        assertEquals(BrushToolRole.UNSUPPORTED, loaded[0].toolBinding.role)
        assertEquals(Compatibility.FAILED, loaded[0].compatibility)
        assertNull(loaded[0].v5PresetJson)
        assertTrue(loaded[0].report.any { it.contains("UNRECOGNIZED_ROLE") })
        assertNotNull(loaded[1].v5PresetJson)
        store.save(loaded)
        assertEquals(loaded[0].toolBinding, store.load()[0].toolBinding)
    }
}
