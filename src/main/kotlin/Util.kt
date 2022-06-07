import org.graalvm.polyglot.*
import org.graalvm.polyglot.proxy.Proxy
import org.graalvm.polyglot.proxy.ProxyObject

const val JS_LANGUAGE: String = "js"

private val hostAccess = HostAccess
    .newBuilder()
    .allowListAccess(true)
    .targetTypeMapping(
        Value::class.java, Any::class.java,
        { v -> v.hasArrayElements() },
        { v -> transformArray(v) }
    ).build()

private val engine = Engine
    .newBuilder()
    .option("js.experimental-foreign-object-prototype", "true")
    .allowExperimentalOptions(true)
    .build()

fun getContext(): Context =
    Context
        .newBuilder()
        .engine(engine)
        .allowHostAccess(hostAccess)
        .build()

fun <T> useContext(execution: (Context) -> T): T =
    getContext().use(execution)

// Util functions
fun <T> Context.evalSource(source: Source, type: TypeLiteral<T>): T {
    return eval(source).`as`(type)
}

fun <T> Context.evalJs(script: String, type: TypeLiteral<T>): T {
    return eval("js", script).`as`(type)
}

private fun transformArray(v: Value): MutableList<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until v.arraySize) {
        val element = v.getArrayElement(i)
        if (element.hasArrayElements() && !element.isHostObject) {
            list.add(transformArray(element))
        } else if (element.hasMembers() && !element.isHostObject) {
            list.add(transformMembers(element))
        } else {
            list.add(element.`as`(Any::class.java))
        }
    }
    return list
}

private fun transformMembers(v: Value): Map<*, *> {
    val map: MutableMap<String, Any?> = mutableMapOf()
    for (key in v.memberKeys) {
        val member = v.getMember(key)
        if (member.hasArrayElements() && !member.isHostObject) {
            map[key] = transformArray(member)
        } else if (member.hasMembers() && !member.isHostObject) {
            map[key] = transformMembers(member)
        } else {
            map[key] = member.`as`(Any::class.java)
        }
    }
    return map
}

object JsonMapGraalProxyFactory {
    fun proxyForJsonMap(jsonMap: Map<String, Any?>): Proxy {
        val proxyForJsonMap = jsonMap.mapValues {
            when (val value = it.value) {
                is Map<*, *> -> proxyForJsonMap(value as MutableMap<String, Any?>)
                is List<*> -> proxyForJsonList(value as List<Any?>)
                else -> value
            }
        }
        return ProxyObject.fromMap(proxyForJsonMap)
    }

    fun proxyForJsonList(jsonList: List<Any?>): List<Any?> =
        jsonList.map {
            when (it) {
                is Map<*, *> -> proxyForJsonMap(it as MutableMap<String, Any?>)
                is List<*> -> proxyForJsonList(it as MutableList<Any?>)
                else -> it
            }
        }
}
