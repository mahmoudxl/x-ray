package com.clinic.orthopedic

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.security.MessageDigest

private val Teal = Color(0xFF087E8B)
private val Ink = Color(0xFF183044)
private val Bg = Color(0xFFF4F7FA)

class ClinicDb(context: Context) : android.database.sqlite.SQLiteOpenHelper(context, "orthopedic_clinic.db", null, 1) {
    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("CREATE TABLE patients(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, age TEXT, gender TEXT, address TEXT, notes TEXT, created TEXT)")
        db.execSQL("CREATE TABLE visits(id INTEGER PRIMARY KEY AUTOINCREMENT, patientId INTEGER NOT NULL, date TEXT, type TEXT, fee REAL, paid REAL, notes TEXT)")
    }
    override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun addPatient(name:String, phone:String, age:String, gender:String, address:String, notes:String) {
        val v=ContentValues().apply { put("name",name);put("phone",phone);put("age",age);put("gender",gender);put("address",address);put("notes",notes);put("created",today()) }
        writableDatabase.insert("patients",null,v)
    }
    fun updatePatient(id:Int,name:String,phone:String,age:String,gender:String,address:String,notes:String) {
        val v=ContentValues().apply { put("name",name);put("phone",phone);put("age",age);put("gender",gender);put("address",address);put("notes",notes) }
        writableDatabase.update("patients",v,"id=?",arrayOf(id.toString()))
    }
    fun deletePatient(id:Int) { writableDatabase.delete("visits","patientId=?",arrayOf(id.toString()));writableDatabase.delete("patients","id=?",arrayOf(id.toString())) }
    fun addVisit(pid:Int,type:String,fee:Double,paid:Double,notes:String) {
        val v=ContentValues().apply { put("patientId",pid);put("date",today());put("type",type);put("fee",fee);put("paid",paid);put("notes",notes) }
        writableDatabase.insert("visits",null,v)
    }
    fun updateVisit(id:Int,type:String,fee:Double,paid:Double,notes:String) {
        val v=ContentValues().apply { put("type",type);put("fee",fee);put("paid",paid);put("notes",notes) }
        writableDatabase.update("visits",v,"id=?",arrayOf(id.toString()))
    }
    fun deleteVisit(id:Int) { writableDatabase.delete("visits","id=?",arrayOf(id.toString())) }
    fun patients():List<Patient> {
        val out= mutableListOf<Patient>(); readableDatabase.rawQuery("SELECT * FROM patients ORDER BY id DESC",null).use { c -> while(c.moveToNext()) out.add(Patient(c.getInt(0),c.getString(1),c.getString(2)?:"",c.getString(3)?:"",c.getString(4)?:"",c.getString(5)?:"",c.getString(6)?:"")) }; return out
    }
    fun visits(pid:Int?=null):List<Visit> {
        val out= mutableListOf<Visit>(); val sql=if(pid==null) "SELECT v.id,v.patientId,p.name,v.date,v.type,v.fee,v.paid,v.notes FROM visits v JOIN patients p ON p.id=v.patientId ORDER BY v.id DESC" else "SELECT v.id,v.patientId,p.name,v.date,v.type,v.fee,v.paid,v.notes FROM visits v JOIN patients p ON p.id=v.patientId WHERE v.patientId=$pid ORDER BY v.id DESC"
        readableDatabase.rawQuery(sql,null).use { c -> while(c.moveToNext()) out.add(Visit(c.getInt(0),c.getInt(1),c.getString(2),c.getString(3)?:"",c.getString(4)?:"",c.getDouble(5),c.getDouble(6),c.getString(7)?:"")) }; return out
    }
    fun totals(period:String):Pair<Double,Double> {
        val start=when(period){"اليوم"->today();"الشهر"->today().take(7);else->""}
        val sql=if(period=="كل الفترات") "SELECT COALESCE(SUM(fee),0),COALESCE(SUM(paid),0) FROM visits" else "SELECT COALESCE(SUM(fee),0),COALESCE(SUM(paid),0) FROM visits WHERE date LIKE '${if(period=="اليوم") start else "$start%"}%'"
        readableDatabase.rawQuery(sql,null).use { c -> if(c.moveToFirst()) return c.getDouble(0) to c.getDouble(1) }; return 0.0 to 0.0
    }
    fun patientCount()=readableDatabase.rawQuery("SELECT COUNT(*) FROM patients",null).use{it.moveToFirst();it.getInt(0)}
    fun todayVisits()=readableDatabase.rawQuery("SELECT COUNT(*) FROM visits WHERE date=?",arrayOf(today())).use{it.moveToFirst();it.getInt(0)}
}

data class Patient(val id:Int,val name:String,val phone:String,val age:String,val gender:String,val address:String,val notes:String)
data class Visit(val id:Int,val patientId:Int,val patientName:String,val date:String,val type:String,val fee:Double,val paid:Double,val notes:String)
private fun today()=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
private fun money(v:Double)=String.format(Locale.US,"%.2f",v)
private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}

class MainActivity : ComponentActivity() {
    private lateinit var db:ClinicDb
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); db=ClinicDb(this); setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Teal,background=Bg)) { ClinicApp(db) } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ClinicApp(db:ClinicDb) {
    val ctx= LocalContext.current
    val prefs=ctx.getSharedPreferences("clinic_settings",Context.MODE_PRIVATE)
    var logged by remember { mutableStateOf(false) }; var role by remember { mutableStateOf("سكرتير") }; var username by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("الرئيسية") }; var refresh by remember { mutableIntStateOf(0) }; var selected by remember { mutableStateOf<Patient?>(null) }
    val prices= mapOf("كشف" to prefs.getFloat("fee_exam",150f).toDouble(),"استشارة" to prefs.getFloat("fee_consult",100f).toDouble(),"متابعة" to prefs.getFloat("fee_follow",75f).toDouble())
    val backupLauncher= rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if(uri!=null) try { ctx.contentResolver.openOutputStream(uri)?.use { out -> File(ctx.getDatabasePath("orthopedic_clinic.db").path).inputStream().use{it.copyTo(out)} }; Toast.makeText(ctx,"تم إنشاء النسخة الاحتياطية",Toast.LENGTH_LONG).show() } catch(e:Exception){Toast.makeText(ctx,"تعذر إنشاء النسخة",Toast.LENGTH_LONG).show()} }
    val restoreLauncher= rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) try { ctx.contentResolver.openInputStream(uri)?.use { input -> val target=File(ctx.getDatabasePath("orthopedic_clinic.db").path); target.parentFile?.mkdirs(); target.outputStream().use{input.copyTo(it)} }; Toast.makeText(ctx,"تمت الاستعادة. أعد فتح التطبيق.",Toast.LENGTH_LONG).show() } catch(e:Exception){Toast.makeText(ctx,"تعذرت الاستعادة",Toast.LENGTH_LONG).show()} }
    if(!logged) { LoginScreen(prefs){ r,u -> role=r;username=u;logged=true } ; return }
    Scaffold(containerColor=Bg, topBar={ TopAppBar(title={Column { Text("إدارة عيادة العظام",fontWeight=FontWeight.Bold,color=Ink);Text("مرحباً $username · $role",fontSize=12.sp,color=Color.Gray)}},actions={ IconButton(onClick={logged=false}){Icon(Icons.Default.Logout,"خروج")} }) }, bottomBar={ NavigationBar { (if(role=="مدير") listOf("الرئيسية","المرضى","الزيارات","التقارير","الإعدادات") else listOf("الرئيسية","المرضى","الزيارات","التقارير")).forEach { item -> NavigationBarItem(selected=page==item,onClick={page=item;selected=null;refresh++},icon={Icon(when(item){"الرئيسية"->Icons.Default.Home;"المرضى"->Icons.Default.People;"الزيارات"->Icons.Default.EventNote;"التقارير"->Icons.Default.BarChart;else->Icons.Default.Settings},null)},label={Text(item,fontSize=10.sp)}) } } }) { pad ->
        key(refresh,page) { Box(Modifier.padding(pad).fillMaxSize()) { when(page) {
            "الرئيسية" -> HomeScreen(db){page=it;refresh++}
            "المرضى" -> PatientsScreen(db,selected,{selected=it;page="ملف المريض";refresh++},{refresh++})
            "ملف المريض" -> selected?.let { PatientScreen(db,it,prices,{page="المرضى";refresh++},{refresh++},ctx) } ?: PatientsScreen(db,null,{selected=it;page="ملف المريض"},{refresh++})
            "الزيارات" -> VisitsScreen(db,prices,{refresh++},ctx)
            "التقارير" -> ReportsScreen(db)
            else -> SettingsScreen(prefs,role,{backupLauncher.launch("clinic-backup-${today()}.db")},{restoreLauncher.launch(arrayOf("*/*"))})
        } } }
    }
}

@Composable private fun LoginScreen(prefs:android.content.SharedPreferences,onLogin:(String,String)->Unit) {
    var user by remember { mutableStateOf("manager") }; var pass by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
    val managerHash=prefs.getString("manager_hash",null); val secretaryHash=prefs.getString("secretary_hash",null)
    Column(Modifier.fillMaxSize().background(Bg).padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Default.MedicalServices,null,tint=Teal,modifier=Modifier.size(64.dp)); Text("عيادة العظام",fontSize=27.sp,fontWeight=FontWeight.Bold,color=Ink); Text("نظام إدارة السكرتارية",color=Color.Gray)
        Spacer(Modifier.height(28.dp)); Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("تسجيل الدخول",fontWeight=FontWeight.Bold,fontSize=20.sp)
            OutlinedTextField(user,{user=it},label={Text("اسم المستخدم")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(pass,{pass=it},label={Text("كلمة المرور")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth(),singleLine=true)
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            Button(onClick={
                val isManager= if(managerHash==null) user=="manager"&&pass=="1234" else user=="manager"&&hash(pass)==managerHash
                val isSecretary= if(secretaryHash==null) user=="secretary"&&pass=="1234" else user=="secretary"&&hash(pass)==secretaryHash
                if(isManager||isSecretary) onLogin(if(isManager)"مدير" else "سكرتير",if(isManager)"المدير" else "السكرتير") else error="اسم المستخدم أو كلمة المرور غير صحيحة"
            },modifier=Modifier.fillMaxWidth()) { Text("دخول") }
            Text("الدخول الأولي: manager / 1234 أو secretary / 1234. غيّر كلمات المرور من الإعدادات.",fontSize=11.sp,color=Color.Gray)
        } }
    }
}

@Composable private fun HomeScreen(db:ClinicDb,onNavigate:(String)->Unit) {
    val count=db.patientCount(); val visits=db.todayVisits(); val total=db.totals("اليوم")
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("ملخص العيادة",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Ink);Text(today(),color=Color.Gray) }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { StatCard("إجمالي المرضى",count.toString(),Icons.Default.People,Modifier.weight(1f));StatCard("زيارات اليوم",visits.toString(),Icons.Default.EventAvailable,Modifier.weight(1f)) } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { StatCard("مستحق اليوم",money(total.first)+" ج",Icons.Default.Receipt,Modifier.weight(1f));StatCard("المحصل اليوم",money(total.second)+" ج",Icons.Default.Payments,Modifier.weight(1f)) } }
        item { Text("الوصول السريع",fontWeight=FontWeight.Bold,fontSize=18.sp) }
        item { listOf("المرضى","الزيارات","التقارير").forEach { label -> OutlinedButton(onClick={onNavigate(label)},modifier=Modifier.fillMaxWidth().padding(vertical=3.dp)){Text("فتح $label")} } }
    }
}
@Composable private fun StatCard(title:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier) { Card(modifier,shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.padding(15.dp)) { Icon(icon,null,tint=Teal);Text(title,color=Color.Gray,fontSize=12.sp);Text(value,fontWeight=FontWeight.Bold,fontSize=20.sp,color=Ink) } } }

@Composable private fun PatientsScreen(db:ClinicDb,selected:Patient?,onSelect:(Patient)->Unit,onChanged:()->Unit) {
    var query by remember { mutableStateOf("") }; var showAdd by remember { mutableStateOf(false) }
    val patients=db.patients().filter{it.name.contains(query,true)||it.phone.contains(query)}
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Text("ملفات المرضى",fontSize=21.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Button(onClick={showAdd=true}){Icon(Icons.Default.Add,null);Text("مريض جديد")} }
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth().padding(vertical=10.dp),label={Text("ابحث بالاسم أو رقم الهاتف")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { items(patients){p-> Card(Modifier.fillMaxWidth().clickable{onSelect(p)},shape=RoundedCornerShape(14.dp)) { Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text("${p.phone}  ·  ملف رقم ${p.id}",color=Color.Gray,fontSize=12.sp)};Icon(Icons.Default.ChevronLeft,null,tint=Teal) } } } }
    }
    if(showAdd) PatientForm(null,{name,phone,age,gender,address,notes->db.addPatient(name,phone,age,gender,address,notes);showAdd=false;onChanged()},{showAdd=false})
}

@Composable private fun PatientForm(existing:Patient?,save:(String,String,String,String,String,String)->Unit,onDismiss:()->Unit) {
    var name by remember { mutableStateOf(existing?.name?:"") };var phone by remember { mutableStateOf(existing?.phone?:"") };var age by remember { mutableStateOf(existing?.age?:"") };var gender by remember { mutableStateOf(existing?.gender?:"ذكر") };var address by remember { mutableStateOf(existing?.address?:"") };var notes by remember { mutableStateOf(existing?.notes?:"") }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null)"إضافة مريض" else "تعديل بيانات المريض")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)) { OutlinedTextField(name,{name=it},label={Text("اسم المريض *")},singleLine=true);OutlinedTextField(phone,{phone=it},label={Text("رقم الهاتف")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),singleLine=true);OutlinedTextField(age,{age=it},label={Text("السن")},singleLine=true);Row(verticalAlignment=Alignment.CenterVertically){Text("النوع");Spacer(Modifier.width(12.dp));FilterChip(gender=="ذكر",{gender="ذكر"},label={Text("ذكر")});Spacer(Modifier.width(6.dp));FilterChip(gender=="أنثى",{gender="أنثى"},label={Text("أنثى")})};OutlinedTextField(address,{address=it},label={Text("العنوان")});OutlinedTextField(notes,{notes=it},label={Text("ملاحظات إدارية")}) }},confirmButton={TextButton(onClick={if(name.isNotBlank())save(name,phone,age,gender,address,notes)}){Text("حفظ")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable private fun PatientScreen(db:ClinicDb,p:Patient,prices:Map<String,Double>,back:()->Unit,changed:()->Unit,ctx:Context) {
    var edit by remember { mutableStateOf(false) };var addVisit by remember { mutableStateOf(false) };var confirmDelete by remember { mutableStateOf(false) }
    val visits=db.visits(p.id); val due=visits.sumOf{it.fee-it.paid}
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=back){Icon(Icons.Default.ArrowBack,null)};Text("ملف المريض",fontWeight=FontWeight.Bold,fontSize=21.sp)}
        Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(p.name,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("رقم الملف: ${p.id}   ·   ${p.phone}");Text("السن: ${p.age}   ·   ${p.gender}");Text("العنوان: ${p.address}");Text("الرصيد المستحق: ${money(due)} ج",color=if(due>0)Color(0xFFB45309) else Teal,fontWeight=FontWeight.Bold)} }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={addVisit=true},modifier=Modifier.weight(1f)){Text("إضافة زيارة")};OutlinedButton(onClick={edit=true},modifier=Modifier.weight(1f)){Text("تعديل البيانات")};OutlinedButton(onClick={confirmDelete=true}){Icon(Icons.Default.Delete,null)} }
        Text("سجل الزيارات",fontWeight=FontWeight.Bold,fontSize=18.sp,modifier=Modifier.padding(vertical=10.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(visits){v->VisitCard(v,{type,fee,paid,notes->db.updateVisit(v.id,type,fee,paid,notes);changed()},{db.deleteVisit(v.id);changed()},ctx)} }
    }
    if(edit) PatientForm(p,{n,ph,a,g,ad,no->db.updatePatient(p.id,n,ph,a,g,ad,no);edit=false;changed()},{edit=false})
    if(addVisit) VisitForm(p,prices,{type,fee,paid,notes->db.addVisit(p.id,type,fee,paid,notes);addVisit=false;changed()},{addVisit=false})
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("حذف ملف المريض؟")},text={Text("سيتم حذف جميع زياراته أيضاً. لا يمكن التراجع عن الحذف.")},confirmButton={TextButton(onClick={db.deletePatient(p.id);confirmDelete=false;back()}){Text("حذف",color=Color.Red)}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("إلغاء")}})
}

@Composable private fun VisitsScreen(db:ClinicDb,prices:Map<String,Double>,changed:()->Unit,ctx:Context) {
    var query by remember { mutableStateOf("") }; var choosing by remember { mutableStateOf(false) }; var chosen by remember { mutableStateOf<Patient?>(null) }
    val patients=db.patients(); val visits=db.visits().filter{it.patientName.contains(query,true)}
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Text("الزيارات",fontWeight=FontWeight.Bold,fontSize=21.sp,modifier=Modifier.weight(1f));Button(onClick={choosing=true}){Text("زيارة جديدة")}}
        OutlinedTextField(query,{query=it},label={Text("بحث باسم المريض")},modifier=Modifier.fillMaxWidth().padding(vertical=8.dp),singleLine=true)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(visits){v->VisitCard(v,{type,fee,paid,notes->db.updateVisit(v.id,type,fee,paid,notes);changed()},{db.deleteVisit(v.id);changed()},ctx)}}
    }
    if(choosing) AlertDialog(onDismissRequest={choosing=false},title={Text("اختيار المريض")},text={if(patients.isEmpty()) Text("أضف مريضاً أولاً من شاشة المرضى") else LazyColumn { items(patients){p->Row(Modifier.fillMaxWidth().clickable{chosen=p}.padding(8.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(chosen?.id==p.id,{chosen=p});Text(p.name)} } } },confirmButton={TextButton(onClick={if(chosen!=null)choosing=false}){Text("متابعة")}},dismissButton={TextButton(onClick={choosing=false}){Text("إلغاء")}})
    if(!choosing && chosen!=null) VisitForm(chosen!!,prices,{type,fee,paid,notes->db.addVisit(chosen!!.id,type,fee,paid,notes);chosen=null;changed()},{chosen=null})
}

@Composable private fun VisitForm(p:Patient,prices:Map<String,Double>,save:(String,Double,Double,String)->Unit,onDismiss:()->Unit) {
    var type by remember { mutableStateOf("كشف") };var fee by remember { mutableStateOf(money(prices[type]?:0.0)) };var paid by remember { mutableStateOf(fee) };var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("زيارة جديدة · ${p.name}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("نوع الزيارة");Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("كشف","استشارة","متابعة").forEach{FilterChip(type==it,{type=it;fee=money(prices[it]?:0.0);paid=fee},label={Text(it)})}};OutlinedTextField(fee,{fee=it},label={Text("قيمة الزيارة")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true);OutlinedTextField(paid,{paid=it},label={Text("المبلغ المدفوع")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true);OutlinedTextField(notes,{notes=it},label={Text("ملاحظات إدارية")})}},confirmButton={TextButton(onClick={val f=fee.toDoubleOrNull();val a=paid.toDoubleOrNull();if(f!=null&&a!=null&&f>=0&&a>=0&&a<=f)save(type,f,a,notes)}){Text("حفظ الزيارة")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable private fun VisitCard(v:Visit,onUpdate:(String,Double,Double,String)->Unit,onDelete:()->Unit,ctx:Context) {
    var menu by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf(false) }; var type by remember { mutableStateOf(v.type) }; var fee by remember { mutableStateOf(money(v.fee)) }; var paid by remember { mutableStateOf(money(v.paid)) }; var notes by remember { mutableStateOf(v.notes) }
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(13.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(v.patientName,fontWeight=FontWeight.Bold);Text("${v.type} · ${v.date}",color=Color.Gray,fontSize=12.sp)};Text("${money(v.fee)} ج",fontWeight=FontWeight.Bold,color=Ink);Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,null)};DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("تعديل")},onClick={menu=false;editing=true});DropdownMenuItem(text={Text("حذف")},onClick={menu=false;onDelete()});DropdownMenuItem(text={Text("طباعة إيصال")},onClick={menu=false;printReceipt(ctx,v)})}}};Text("مدفوع: ${money(v.paid)} ج   ·   متبقي: ${money(v.fee-v.paid)} ج",fontSize=12.sp,color=if(v.fee>v.paid)Color(0xFFB45309) else Teal);if(v.notes.isNotBlank())Text(v.notes,fontSize=12.sp,color=Color.Gray)} }
    if(editing) AlertDialog(onDismissRequest={editing=false},title={Text("تعديل الزيارة")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("كشف","استشارة","متابعة").forEach{FilterChip(type==it,{type=it},label={Text(it)})}};OutlinedTextField(fee,{fee=it},label={Text("قيمة الزيارة")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));OutlinedTextField(paid,{paid=it},label={Text("المدفوع")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));OutlinedTextField(notes,{notes=it},label={Text("ملاحظات")})}},confirmButton={TextButton(onClick={val f=fee.toDoubleOrNull();val a=paid.toDoubleOrNull();if(f!=null&&a!=null&&f>=0&&a>=0&&a<=f){onUpdate(type,f,a,notes);editing=false}}){Text("حفظ")}},dismissButton={TextButton(onClick={editing=false}){Text("إلغاء")}})
}

private fun printReceipt(ctx:Context,v:Visit) {
    val html="<html dir='rtl'><body style='font-family:sans-serif;padding:28px'><h2>إيصال عيادة العظام</h2><hr/><p>رقم الزيارة: ${v.id}</p><p>اسم المريض: ${v.patientName}</p><p>التاريخ: ${v.date}</p><p>نوع الخدمة: ${v.type}</p><p>قيمة الزيارة: ${money(v.fee)} جنيه</p><p>المدفوع: ${money(v.paid)} جنيه</p><p>المتبقي: ${money(v.fee-v.paid)} جنيه</p><hr/><p>شكراً لزيارتكم</p></body></html>"
    val web=WebView(ctx);web.loadDataWithBaseURL(null,html,"text/HTML","UTF-8",null);web.webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView,url:String?){val pm=ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager;pm.print("إيصال-${v.id}",view.createPrintDocumentAdapter("إيصال-${v.id}"),PrintAttributes.Builder().build())}}
}

@Composable private fun ReportsScreen(db:ClinicDb) {
    var period by remember { mutableStateOf("اليوم") };val sums=db.totals(period);val visits=db.visits().filter{when(period){"اليوم"->it.date==today();"الشهر"->it.date.startsWith(today().take(7));else->true}}
    Column(Modifier.fillMaxSize().padding(16.dp)){Text("تقارير الإيرادات",fontSize=22.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("اليوم","الشهر","كل الفترات").forEach{FilterChip(period==it,{period=it},label={Text(it)})}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){StatCard("إجمالي المستحق",money(sums.first)+" ج",Icons.Default.Receipt,Modifier.weight(1f));StatCard("إجمالي المحصل",money(sums.second)+" ج",Icons.Default.Payments,Modifier.weight(1f))};StatCard("المتبقي",money(sums.first-sums.second)+" ج",Icons.Default.AccountBalanceWallet,Modifier.fillMaxWidth());Text("تفاصيل الزيارات (${visits.size})",fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=10.dp));LazyColumn{items(visits){v->ListItem(headlineContent={Text(v.patientName)},supportingContent={Text("${v.date} · ${v.type}")},trailingContent={Text("${money(v.paid)} ج")})}} }
}

@Composable private fun SettingsScreen(prefs:android.content.SharedPreferences,role:String,onBackup:()->Unit,onRestore:()->Unit) {
    var exam by remember { mutableStateOf(prefs.getFloat("fee_exam",150f).toString()) };var consult by remember { mutableStateOf(prefs.getFloat("fee_consult",100f).toString()) };var follow by remember { mutableStateOf(prefs.getFloat("fee_follow",75f).toString()) }
    var managerPass by remember { mutableStateOf("") };var secretaryPass by remember { mutableStateOf("") };var msg by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("الإعدادات",fontWeight=FontWeight.Bold,fontSize=22.sp)};item{Card{Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("أسعار الخدمات",fontWeight=FontWeight.Bold);OutlinedTextField(exam,{exam=it},label={Text("سعر الكشف")});OutlinedTextField(consult,{consult=it},label={Text("سعر الاستشارة")});OutlinedTextField(follow,{follow=it},label={Text("سعر المتابعة")});Button(onClick={val a=exam.toFloatOrNull();val b=consult.toFloatOrNull();val c=follow.toFloatOrNull();if(a!=null&&b!=null&&c!=null){prefs.edit().putFloat("fee_exam",a).putFloat("fee_consult",b).putFloat("fee_follow",c).apply();msg="تم حفظ الأسعار"}else msg="راجع الأسعار المدخلة"}){Text("حفظ الأسعار")} }}};item{if(role=="مدير")Card{Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("إدارة كلمات المرور",fontWeight=FontWeight.Bold);OutlinedTextField(managerPass,{managerPass=it},label={Text("كلمة مرور المدير الجديدة")},visualTransformation=PasswordVisualTransformation());OutlinedTextField(secretaryPass,{secretaryPass=it},label={Text("كلمة مرور السكرتير الجديدة")},visualTransformation=PasswordVisualTransformation());Button(onClick={val e=prefs.edit();if(managerPass.isNotBlank())e.putString("manager_hash",hash(managerPass));if(secretaryPass.isNotBlank())e.putString("secretary_hash",hash(secretaryPass));e.apply();managerPass="";secretaryPass="";msg="تم تحديث كلمات المرور"}){Text("حفظ كلمات المرور")}}}};item{Card{Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("النسخ الاحتياطي",fontWeight=FontWeight.Bold);Text("احفظ نسخة من قاعدة بيانات العيادة في مكان آمن، واستعدها عند تغيير الجهاز.",fontSize=12.sp,color=Color.Gray);Button(onClick=onBackup,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Backup,null);Text("إنشاء نسخة احتياطية")};OutlinedButton(onClick=onRestore,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Restore,null);Text("استعادة نسخة احتياطية")}}}};item{if(msg.isNotBlank())Text(msg,color=Teal)};item{Text("الإصدار 1.0.0 · يعمل محلياً دون إنترنت",color=Color.Gray,fontSize=11.sp)} }
}
