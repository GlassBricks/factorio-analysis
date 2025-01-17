package glassbricks.factorio.prototypes

import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test


class LoadKtTest {
    @Test
    fun `can load and save data raw`() {
        val dataRaw = SpaceAgeDataRaw
        DataRawJson.encodeToJsonElement(DataRaw.serializer(), dataRaw)
        val pipe = dataRaw.lab.values.first()
        val pipeStr = DataRawJson.encodeToJsonElement(pipe)
        println(pipeStr)
    }
}
