package app.inkloom.importer
import app.inkloom.model.*
import org.junit.Assert.*
import org.junit.Test

class BrushFolderImportTest {
    private fun dir(id:String,name:String=id)=BrushFolderImport.Node(id,name,true)
    private fun file(id:String,name:String=id)=BrushFolderImport.Node(id,name,false)
    @Test fun preservesRootNestedAndEmptyGroupsAndCountsPreviewFiles() {
        val plan=BrushFolderImport.scan(dir("root","CSP 笔刷"),{ id -> when(id) {
            "root" -> listOf(dir("ink","勾线"),dir("paint","绘画"),file("cover.png"))
            "ink" -> listOf(file("pen.sut"),dir("nested","软笔"))
            "paint" -> emptyList()
            else -> listOf(file("soft.SUT"))
        } })
        assertEquals(listOf("CSP 笔刷","CSP 笔刷/勾线","CSP 笔刷/绘画","CSP 笔刷/勾线/软笔"),plan.groups)
        assertEquals(listOf("CSP 笔刷/勾线/pen.sut","CSP 笔刷/勾线/软笔/soft.SUT"),plan.files.map { it.path })
        assertEquals(1,plan.skipped);assertTrue(plan.errors.isEmpty())
    }
    @Test fun unreadableAndCyclicDirectoriesDoNotLoseHealthySiblings() {
        val plan=BrushFolderImport.scan(dir("root"),{ id -> when(id) {
            "root" -> listOf(dir("bad"),dir("good"),dir("root","cycle"))
            "bad" -> error("permission denied")
            else -> listOf(file("good.abr"))
        } })
        assertEquals(2,plan.errors.size);assertEquals(1,plan.files.size)
    }
    @Test fun caseCollisionGetsSeparateStableGroupAndStopTerminatesTraversal() {
        val plan=BrushFolderImport.scan(dir("root"),{ if(it=="root") listOf(dir("1","Ink"),dir("2","ink")) else emptyList() })
        assertEquals(listOf("root","root/Ink","root/ink (2)"),plan.groups)
        assertTrue(BrushFolderImport.scan(dir("root"),{ error("must not enumerate") },{true}).groups.isEmpty())
    }
    @Test fun identicalSourcesInDifferentBranchesStayDistinctAndReimportDoesNotDuplicate() {
        val brush=ImportedBrush(name="pen",format=BrushFormat.SUT,compatibility=Compatibility.COMPLETE,sizeBytes=100,report=emptyList(),sha256="abc")
        val first=BrushFolderImport.assign(BrushFolderImport.Source("1","a.sut","Root/Ink"),brush)
        val second=BrushFolderImport.assign(BrushFolderImport.Source("2","a.sut","Root/Paint"),brush)
        assertNotEquals(first.id,second.id)
        val merged=BrushFolderImport.merge(listOf(first),listOf(first,second))
        assertEquals(2,merged.size);assertEquals(merged,BrushFolderImport.merge(merged,listOf(first,second)))
    }
}
