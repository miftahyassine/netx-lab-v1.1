package com.netxlab.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val dao: NxDao
) : ViewModel() {
    val settings = settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val records = dao.records().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hosts = dao.hosts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val projects = dao.projects().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setTheme(t: AppTheme) = viewModelScope.launch { settingsRepo.setTheme(t) }
    fun setInfoIcons(v: Boolean) = viewModelScope.launch { settingsRepo.setInfoIcons(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { settingsRepo.setHaptics(v) }
    fun setSound(v: Boolean) = viewModelScope.launch { settingsRepo.setSound(v) }
    fun setTimeout(v: Int) = viewModelScope.launch { settingsRepo.setTimeout(v) }
    fun setDeveloper(v: Boolean) = viewModelScope.launch { settingsRepo.setDeveloper(v) }
    fun setConsent(v: Boolean) = viewModelScope.launch { settingsRepo.setConsent(v) }
    fun completeLesson(id: String) = viewModelScope.launch { settingsRepo.completeLesson(id) }
    fun resetLessons() = viewModelScope.launch { settingsRepo.resetLessons() }
    fun setActiveProject(id: Long) = viewModelScope.launch { settingsRepo.setActiveProject(id) }

    fun save(type: String, target: String, summary: String, data: String = "") = viewModelScope.launch {
        val pid = settings.value.activeProject.takeIf { it > 0 }
        com.netxlab.app.core.AppLog.log("$type $target: $summary")
        dao.insertRecord(ScanRecord(type = type, target = target, summary = summary, data = data, projectId = pid))
    }
    fun deleteRecord(r: ScanRecord) = viewModelScope.launch { dao.deleteRecord(r) }
    fun clearHistory() = viewModelScope.launch { dao.clearRecords() }
    fun saveHost(h: SavedHost) = viewModelScope.launch { dao.upsertHost(h) }
    fun deleteHost(h: SavedHost) = viewModelScope.launch { dao.deleteHost(h) }
    fun saveProject(p: Project) = viewModelScope.launch { dao.upsertProject(p) }
    fun deleteProject(p: Project) = viewModelScope.launch { dao.deleteProject(p) }
    fun clearAll() = viewModelScope.launch { dao.clearRecords(); dao.clearHosts(); dao.clearProjects() }

    suspend fun exportJson(): String {
        val o = JSONObject()
        o.put("app", "NETX LAB"); o.put("version", 1)
        o.put("records", JSONArray().also { a -> dao.allRecords().forEach { r ->
            a.put(JSONObject().put("type", r.type).put("target", r.target).put("summary", r.summary)
                .put("data", r.data).put("projectId", r.projectId ?: JSONObject.NULL).put("timestamp", r.timestamp)) } })
        o.put("hosts", JSONArray().also { a -> dao.allHosts().forEach { h ->
            a.put(JSONObject().put("address", h.address).put("alias", h.alias).put("note", h.note)
                .put("favorite", h.favorite).put("authorized", h.authorized).put("scope", h.scope)) } })
        o.put("projects", JSONArray().also { a -> dao.allProjects().forEach { p ->
            a.put(JSONObject().put("name", p.name).put("notes", p.notes).put("created", p.created)) } })
        return o.toString(2)
    }

    fun importJson(text: String, onDone: (String) -> Unit) = viewModelScope.launch {
        try {
            val o = JSONObject(text)
            require(o.optString("app") == "NETX LAB") { "Not a NETX LAB export" }
            var n = 0
            o.optJSONArray("records")?.let { a -> for (i in 0 until a.length()) { val r = a.getJSONObject(i)
                dao.insertRecord(ScanRecord(type = r.getString("type"), target = r.getString("target"),
                    summary = r.getString("summary"), data = r.optString("data"), timestamp = r.optLong("timestamp"))); n++ } }
            o.optJSONArray("hosts")?.let { a -> for (i in 0 until a.length()) { val h = a.getJSONObject(i)
                dao.upsertHost(SavedHost(address = h.getString("address"), alias = h.optString("alias"), note = h.optString("note"),
                    favorite = h.optBoolean("favorite"), authorized = h.optBoolean("authorized"), scope = h.optString("scope"))); n++ } }
            o.optJSONArray("projects")?.let { a -> for (i in 0 until a.length()) { val p = a.getJSONObject(i)
                dao.upsertProject(Project(name = p.getString("name"), notes = p.optString("notes"))); n++ } }
            onDone("Imported $n items")
        } catch (e: Exception) { onDone("Import failed: ${e.message}") }
    }
}
