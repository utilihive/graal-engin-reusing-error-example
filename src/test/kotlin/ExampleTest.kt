import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.TypeLiteral
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class ExampleTest  {

    private val mapper = jacksonObjectMapper()
    private val jsonMapTypeReference =
        object : TypeReference<MutableMap<String, Any?>>() {}
    private val jsonListTypeReference =
        object : TypeReference<List<Any?>>() {}
    private val jsonBeanListTypeReference =
        object : TypeReference<List<Bean?>>() {}

    @Test
    fun evalJs_convertableValueWithType_valueConvertedAsExpected() {
        useContext { context ->
            assertThat(context.evalJs("[1 == 1]", object : TypeLiteral<List<Boolean>>() {})).containsExactly(true)
        }
    }

    @Test
    fun evalSource_convertableValueWithType_valueConvertedAsExpected() {
        useContext { context ->
            val source = Source
                .newBuilder(JS_LANGUAGE, "[1 == 1]", "evalSource_convertableValueWithType")
                .build()
            assertThat(context.evalSource(source, object : TypeLiteral<List<Boolean>>() {})).containsExactly(true)
        }
    }


    @Test
    fun proxyForJsonMap_normal_originalMapIsAvailableAsAJavaScriptObjectInTheScript() {
        @Language("ECMAScript 6")
        val script = """
            (
                [
                    {
                        list:[{a:input.list[0].a}]
                    },
                    input
                ]
            )
        """.trimIndent()
        val bean = Bean(
            list = listOf(
                BeanListMember(a = "aValue")
            )
        )
        val jsonMap = mapper.convertValue(bean, jsonMapTypeReference)
        val mapProxy = JsonMapGraalProxyFactory.proxyForJsonMap(jsonMap)
        useContext { context ->
            val scriptBinding = context.getBindings(JS_LANGUAGE)
            scriptBinding.putMember("input", mapProxy)
            val output = context.evalJs(script, object : TypeLiteral<List<Any?>>() {})
            val result = mapper.convertValue(output, jsonBeanListTypeReference)
            assertThat(result).hasSize(2)
            assertThat(result[0]).isEqualTo(bean)
            assertThat(result[1]).isEqualTo(bean)
        }
    }


    @Test
    fun proxyForJsonList_normal_originalListIsAvailableAsAJavaScriptArrayInTheScript() {
        @Language("ECMAScript 6")
        val script = """
            (
                [
                
                    {
                        list:[{a:input[0].list[0].a}]
                    },
                    input[0]
                ]
            )
        """.trimIndent()
        val beanList = listOf(
            Bean(
                list = listOf(
                    BeanListMember(
                        a = "aValue"
                    )
                )
            )
        )
        val jsonList = mapper.convertValue(beanList, jsonListTypeReference)
        val listProxy = JsonMapGraalProxyFactory.proxyForJsonList(jsonList)
        useContext { context ->
            val scriptBinding = context.getBindings(JS_LANGUAGE)
            scriptBinding.putMember("input", listProxy)
            val output = context.evalJs(script, object : TypeLiteral<List<Any?>>() {})
            val result = mapper.convertValue(output, jsonBeanListTypeReference)
            assertThat(result).hasSize(2)
            assertThat(result[0]).isEqualTo(beanList[0])
            assertThat(result[1]).isEqualTo(beanList[0])
        }
    }

    data class Bean(
        val list: List<BeanListMember>
    )

    data class BeanListMember(
        val a: String
    )
}

