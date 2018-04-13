package models

// Importes necesarios para hacer el modelo o POJO
import com.sun.org.apache.xpath.internal.operations.Or
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.libs.functional.syntax._

// Se crea la clase Agency, donde los parametros deben coincidir con los campos de la tabla Agency de la base de datos
case class Agency(nit: String, name: String, description: String)

// Y se crea un objeto Agency con el fin de implementar los metodos para leer/escribir Agency como si fuera un Json
object Agency
{
  implicit val agencyWrite = Json.writes[Agency]
  implicit val agencyRead = Json.reads[Agency]
}