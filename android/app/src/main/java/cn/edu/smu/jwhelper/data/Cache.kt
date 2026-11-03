package cn.edu.smu.jwhelper.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地缓存序列化。
 *
 * 目的是「打开就有东西看」：首次启动仍然要联网，之后从磁盘读，几百毫秒内出界面，
 * 网络请求放到后台去更新。
 */
object Cache {

    fun coursesToJson(list: List<Course>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("n", c.name)
                    .put("t", c.teacher)
                    .put("p", c.place)
                    .put("wd", c.weekDay)
                    .put("st", c.startTime)
                    .put("et", c.endTime)
                    .put("w", c.weeks.joinToString(","))
                    .put("sp", c.startP)
                    .put("ep", c.endP)
                    .put("kb", c.kcbh)
                    .put("sg", c.stage)
                    .put("cn", c.classname)
                    .put("nr", if (c.noRoom) 1 else 0)
                    .put("pw", JSONObject().apply {
                        c.placeByWeek.forEach { (w, p) -> put(w.toString(), p) }
                    })
            )
        }
        return arr.toString()
    }

    fun coursesFrom(json: String): List<Course> {
        if (json.isBlank()) return emptyList()
        val out = ArrayList<Course>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pw = HashMap<Int, String>()
                o.optJSONObject("pw")?.let { obj ->
                    obj.keys().forEach { k ->
                        val w = k.toIntOrNull()
                        if (w != null) pw[w] = obj.optString(k)
                    }
                }
                out.add(
                    Course(
                        name = o.optString("n"),
                        teacher = o.optString("t"),
                        place = o.optString("p"),
                        weekDay = o.optInt("wd"),
                        startTime = o.optString("st"),
                        endTime = o.optString("et"),
                        weeks = o.optString("w").split(",").mapNotNull { it.trim().toIntOrNull() },
                        startP = o.optInt("sp"),
                        endP = o.optInt("ep"),
                        kcbh = o.optString("kb"),
                        stage = o.optString("sg"),
                        classname = o.optString("cn"),
                        noRoom = o.optInt("nr") == 1,
                        placeByWeek = pw
                    )
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return out
    }

    fun examsToJson(list: List<ExamItem>): String {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("c", e.course).put("d", e.date).put("t", e.time)
                    .put("p", e.place).put("s", e.seat).put("y", e.type)
                    .put("no", e.note).put("w", e.wkh)
            )
        }
        return arr.toString()
    }

    fun examsFrom(json: String): List<ExamItem> {
        if (json.isBlank()) return emptyList()
        val out = ArrayList<ExamItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    ExamItem(
                        course = o.optString("c"),
                        date = o.optString("d"),
                        time = o.optString("t"),
                        place = o.optString("p"),
                        seat = o.optString("s"),
                        type = o.optString("y"),
                        note = o.optString("no"),
                        wkh = o.optString("w")
                    )
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return out
    }

    fun semestersToJson(list: List<Repo.Semester>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("c", it.code).put("n", it.name)) }
        return arr.toString()
    }

    fun semestersFrom(json: String): List<Repo.Semester> {
        if (json.isBlank()) return emptyList()
        val out = ArrayList<Repo.Semester>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(Repo.Semester(o.optString("c"), o.optString("n")))
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return out
    }

    fun wkhToJson(m: Map<String, String>): String {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    fun wkhFrom(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        val out = HashMap<String, String>()
        try {
            val o = JSONObject(json)
            o.keys().forEach { k -> out[k] = o.optString(k) }
        } catch (e: Exception) {
            return emptyMap()
        }
        return out
    }
}
