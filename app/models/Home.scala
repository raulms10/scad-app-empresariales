package models

// Importes necesarios para hacer el modelo o POJO
import com.sun.org.apache.xpath.internal.operations.Or
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.libs.functional.syntax._

// Se crea la clase Home, donde los parametros deben coincidir con los campos de la tabla Home de la base de datos
case class Home(id: Int, name: String, description: String, address: String, latitude: String, longitude: String, city: String, typeH: Int, rating: Double, pricePerNight: Double, thumbnail: String, agencyCode: String)

// Nota:
// La palabra type ya esta reservada por scala por lo que no
// se puede usar como nombre de una variables, a menos que 
// se ponga entre comillas invertidas, asi: `type`
// https://stackoverflow.com/questions/8198797/is-there-a-way-to-use-type-word-as-a-variable-name-in-scala?utm_medium=organic&utm_source=google_rich_qa&utm_campaign=google_rich_qa

// Y se crea un objeto Home con el fin de implementar los metodos para leer/escribir Home como si fuera un Json
object Home
{
  implicit val agencyWrite = Json.writes[Home]
  implicit val agencyRead = Json.reads[Home]
}
