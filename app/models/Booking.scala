package models

// Importes necesarios para hacer el modelo o POJO
import com.sun.org.apache.xpath.internal.operations.Or
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.libs.functional.syntax._
import java.util.Date

// Se crea la clase Booking, donde los parametros deben coincidir con los campos de la tabla Booking de la base de datos
case class Booking(homeId: Int, checkIn: Date, checkOut: Date, idClient: String, bookingId: Int)

// Y se crea un objeto Booking con el fin de implementar los metodos para leer/escribir Booking como si fuera un Json
object Booking
{
  implicit val bookingWrite = Json.writes[Booking]
  implicit val bookingRead = Json.reads[Booking]
}