import java.sql.*;
import java.util.Scanner;
public class HotelReservationSystem  {
    private static final Scanner sc = new Scanner(System.in);
    private static final double TAX_RATE = 0.05;
    static class Room  {
        int roomNo;
        String type;
        double price;
        boolean available;
        Room(int roomNo, String type, double price, boolean available)  {
            this.roomNo = roomNo;
            this.type = type;
            this.price = price;
            this.available = available;
        }
    }
    static class Customer  {
        int id;
        String name;
        String phone;
        String type;
        int points;
        Customer(int id, String name, String phone, String type, int points)  {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.type = type;
            this.points = points;
        }
        double getDiscount()  {
            return type.equalsIgnoreCase("Premium") ? 10.0 : 0.0;
        }
    }
    static class Bill  {
        double roomCharge;
        double discount;
        double tax;
        double total;
        Bill(double price, int nights, double discountPercent)  {
            roomCharge = price * nights;
            discount = roomCharge * discountPercent / 100.0;
            tax = (roomCharge - discount) * TAX_RATE;
            total = roomCharge - discount + tax;
        }
        void display()  {
            System.out.println("\n========== HOTEL BILL ==========");
            System.out.printf("Room charges : Rs.%.2f%n", roomCharge);
            System.out.printf("Discount		: Rs.%.2f%n", discount);
            System.out.printf("Tax (5%%)		: Rs.%.2f%n", tax);
            System.out.printf("Total	: Rs.%.2f%n", total);
            System.out.println("================================");
        }
    }
    static void addRoom()  {
        try (Connection con = DBConnection.getConnection())  {
            System.out.print("Room number: ");
            int no = Integer.parseInt(sc.nextLine());
            System.out.print("Room type: ");
            String type = sc.nextLine().trim();
            System.out.print("Price per night: ");
            double price = Double.parseDouble(sc.nextLine());
            if (no <= 0 || type.isEmpty() || price <= 0)  {
                System.out.println("Enter valid room details.");
                return;
            }
            String sql = "INSERT INTO rooms "
            + "(room_no, room_type, price, available) "
            + "VALUES (?, ?, ?, TRUE)";
            try (PreparedStatement ps = con.prepareStatement(sql))  {
                ps.setInt(1, no);
                ps.setString(2, type);
                ps.setDouble(3, price);
                ps.executeUpdate();
            }
            System.out.println("Room added successfully.");
        }
        catch (SQLException e)  {
            System.out.println("Database error: " + e.getMessage());
        }
        catch (NumberFormatException e)  {
            System.out.println("Enter valid numeric values.");
        }
    }
    static void addCustomer()  {
        try (Connection con = DBConnection.getConnection())  {
            System.out.print("Customer ID: ");
            int id = Integer.parseInt(sc.nextLine());
            System.out.print("Customer name: ");
            String name = sc.nextLine().trim();
            System.out.print("Phone number: ");
            String phone = sc.nextLine().trim();
            System.out.print("Premium customer? (yes/no): ");
            String answer = sc.nextLine();
            if (id <= 0 || name.isEmpty())  {
                System.out.println("Enter valid customer details.");
                return;
            }
            String type = answer.equalsIgnoreCase("yes")
            ? "Premium" : "Regular";
            String sql = "INSERT INTO customers "
            + "(customer_id, name, phone, customer_type) "
            + "VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql))  {
                ps.setInt(1, id);
                ps.setString(2, name);
                ps.setString(3, phone);
                ps.setString(4, type);
                ps.executeUpdate();
            }
            System.out.println(type + " customer added successfully.");
        }
        catch (SQLException e)  {
            System.out.println("Database error: " + e.getMessage());
        }
        catch (NumberFormatException e)  {
            System.out.println("Customer ID must be numeric.");
        }
    }
    static Room findAvailableRoom(Connection con, int roomNo) throws SQLException  {
        String sql = "SELECT room_no, room_type, price, available "+ "FROM rooms WHERE room_no = ? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql))  {
            ps.setInt(1, roomNo);
            try (ResultSet rs = ps.executeQuery())  {
                if (rs.next())  {
                    return new Room( rs.getInt("room_no"), rs.getString("room_type"), rs.getDouble("price"), rs.getBoolean("available")
                    );
                }
            }
        }
        return null;
    }
    static Customer findCustomer(Connection con, int id) throws SQLException  {
        String sql = "SELECT * FROM customers WHERE customer_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql))  {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery())  {
                if (rs.next())  {
                    return new Customer( rs.getInt("customer_id"), rs.getString("name"), rs.getString("phone"), rs.getString("customer_type"), rs.getInt("loyalty_points")
                    );
                }
            }
        }
        return null;
    }
    static void makeReservation()  {
        try (Connection con = DBConnection.getConnection())  {
            con.setAutoCommit(false);
            try  {
                System.out.print("Room number: ");
                int roomNo = Integer.parseInt(sc.nextLine());
                Room room = findAvailableRoom(con, roomNo);
                if (room == null || !room.available)  {
                    System.out.println("Room not found or unavailable.");
                    con.rollback();
                    return;
                }
                System.out.print("Customer ID: ");
                    int customerId = Integer.parseInt(sc.nextLine());
                    Customer customer = findCustomer(con, customerId);
                    if (customer == null)  {
                        System.out.println("Customer not found.");
                        con.rollback();
                        return;
                    }
                    System.out.print("Number of nights: ");
                    int nights = Integer.parseInt(sc.nextLine());
                    if (nights <= 0)  {
                        System.out.println("Nights must be positive.");
                        con.rollback();
                        return;
                    }
                    String updateRoom =
                    "UPDATE rooms SET available = FALSE "
                    + "WHERE room_no = ? AND available = TRUE";
                    try (PreparedStatement ps =
                    con.prepareStatement(updateRoom))  {
                        ps.setInt(1, roomNo);
                        if (ps.executeUpdate() != 1)  {
                            con.rollback();
                            System.out.println("Room is no longer available.");
                            return;
                        }
                    }
                    String insertBooking =
                        "INSERT INTO bookings (room_no, customer_id, nights, status) "
                        + "VALUES (?, ?, ?, 'CONFIRMED')";
                        int bookingId;
                        try (PreparedStatement ps = con.prepareStatement( insertBooking, Statement.RETURN_GENERATED_KEYS))  {
                            ps.setInt(1, roomNo);
                            ps.setInt(2, customerId);
                            ps.setInt(3, nights);
                            ps.executeUpdate();
                            try (ResultSet keys = ps.getGeneratedKeys())  {
                                if (!keys.next())  {
                                    throw new SQLException("Booking ID not generated.");
                                }
                                bookingId = keys.getInt(1);
                            }
                        }
                        Bill bill = new Bill(
                        room.price, nights, customer.getDiscount()
                        );
                        String insertPayment = "INSERT INTO payments "
                        + "(booking_id, room_charge, discount, tax, total) "
                        + "VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement ps =
                        con.prepareStatement(insertPayment))  {
                            ps.setInt(1, bookingId);
                            ps.setDouble(2, bill.roomCharge);
                            ps.setDouble(3, bill.discount);
                            ps.setDouble(4, bill.tax);
                            ps.setDouble(5, bill.total);
                            ps.executeUpdate();
                        }
                        int earnedPoints = (int) (bill.total / 100);
                        if (customer.type.equalsIgnoreCase("Premium"))  {
                            String updatePoints =
                            "UPDATE customers SET loyalty_points = "
                            + "loyalty_points + ? WHERE customer_id = ?";
                            try (PreparedStatement ps =
                            con.prepareStatement(updatePoints))  {
                                ps.setInt(1, earnedPoints);
                                ps.setInt(2, customerId);
                                ps.executeUpdate();
                            }
                        }
                        con.commit();
                        System.out.println("\nReservation confirmed!");
                        System.out.println("Booking ID: " + bookingId);
                        System.out.println("Customer: " + customer.name);
                        System.out.println("Room: " + room.roomNo + " (" + room.type + ")");
                        System.out.println("Nights: " + nights);
                        bill.display();
                        if (customer.type.equalsIgnoreCase("Premium"))  {
                            System.out.println("Loyalty points earned: " + earnedPoints);
                        }
                        System.out.println("Room availability updated: Booked.");
                    }
                    catch (Exception e)  {
                        con.rollback();
                        System.out.println("Reservation failed: " + e.getMessage());
                    }
                    finally  {
                        con.setAutoCommit(true);
                    }
                }
                catch (SQLException e)  {
                    System.out.println("Database error: " + e.getMessage());
                }
        }
        static void cancelReservation()  {
            try (Connection con = DBConnection.getConnection())  {
                con.setAutoCommit(false);
                try  {
                    System.out.print("Booking ID: ");
                    int id = Integer.parseInt(sc.nextLine());
                    int roomNo;
                    String sql = "SELECT room_no FROM bookings "
                    + "WHERE booking_id = ? AND status = 'CONFIRMED' "
                    + "FOR UPDATE";
                    try (PreparedStatement ps = con.prepareStatement(sql))  {
                        ps.setInt(1, id);
                        try (ResultSet rs = ps.executeQuery())  {
                            if (!rs.next())  {
                                System.out.println(
                                "Active booking not found."
                                );
                                con.rollback();
                                return;
                            }
                            roomNo = rs.getInt("room_no");
                        }
                    }
                    try (PreparedStatement ps = con.prepareStatement( "UPDATE bookings SET status = 'CANCELLED' "
                    + "WHERE booking_id = ?"))  {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE rooms SET available = TRUE WHERE room_no = ?"))  {
                        ps.setInt(1, roomNo);
                        ps.executeUpdate();
                    }
                    con.commit();
                    System.out.println("Reservation cancelled successfully.");
                    System.out.println("Room " + roomNo + " is now available.");
                }
                catch (Exception e)  {
                    con.rollback();
                    System.out.println("Cancellation failed: " + e.getMessage());
                }
                finally  {
                    con.setAutoCommit(true);
                }
            }
            catch (SQLException e)  {
                System.out.println("Database error: " + e.getMessage());
            }
        }
        static void displayRooms()  {
            String sql = "SELECT * FROM rooms ORDER BY room_no";
            try (Connection con = DBConnection.getConnection();
            Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql))  {
                System.out.println("\n========== ROOMS ==========");
                while (rs.next())  {
                    System.out.printf(
                    "Room: %d | Type: %s | Price: Rs.%.2f | Status: %s%n", rs.getInt("room_no"),
                    rs.getString("room_type"), rs.getDouble("price"),
                    rs.getBoolean("available") ? "Available" : "Booked"
                    );
                }
            }
            catch (SQLException e)  {
                System.out.println("Database error: " + e.getMessage());
            }
        }
        static void displayCustomers()  {
            String sql = "SELECT * FROM customers ORDER BY customer_id";
            try (Connection con = DBConnection.getConnection();
            Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql))  {
                while (rs.next())  {
                    System.out.printf(
                    "ID: %d | Name: %s | Type: %s | Points: %d%n", rs.getInt("customer_id"),
                    rs.getString("name"), rs.getString("customer_type"), rs.getInt("loyalty_points")
                    );
                }
            }
            catch (SQLException e)  {
                System.out.println("Database error: " + e.getMessage());
            }
        }
        static void displayBookings()  {
            String sql =
            "SELECT b.booking_id, b.room_no, c.name, b.nights, b.status "
            + "FROM bookings b JOIN customers c "
            + "ON b.customer_id = c.customer_id ORDER BY b.booking_id";
            try (Connection con = DBConnection.getConnection();
            Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql))  {
                while (rs.next())  {
                    System.out.printf(
                    "Booking: %d | Customer: %s | Room: %d "
                    + "| Nights: %d | Status: %s%n", rs.getInt("booking_id"), rs.getString("name"), rs.getInt("room_no"), rs.getInt("nights"), rs.getString("status")
                    );
                }
            }
            catch (SQLException e)  {
                System.out.println("Database error: " + e.getMessage());
            }
        }
        static void displayBills()  {
            String sql =
            "SELECT p.payment_id, p.booking_id, p.room_charge, "
            + "p.discount, p.tax, p.total "
            + "FROM payments p ORDER BY p.payment_id";
            try (Connection con = DBConnection.getConnection();
            Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql))  {
                while (rs.next())  {
                    System.out.printf(
                    "Payment: %d | Booking: %d | Charge: Rs.%.2f "
                    + "| Discount: Rs.%.2f | Tax: Rs.%.2f | Total: Rs.%.2f%n", rs.getInt("payment_id"),
                    rs.getInt("booking_id"),
                    rs.getDouble("room_charge"), rs.getDouble("discount"), rs.getDouble("tax"), rs.getDouble("total")
                    );
                }
            }
            catch (SQLException e)  {
                System.out.println("Database error: " + e.getMessage());
            }
        }
        public static void main(String[] args)  {
            try (Connection con = DBConnection.getConnection())  {
                System.out.println("MySQL connected successfully!");
            }
            catch (SQLException e)  {
                System.out.println("Cannot connect to MySQL: " + e.getMessage());
                System.out.println("Check the server, database and password.");
                return;
            }
            while (true)  {
                System.out.println("\n===== HOTEL RESERVATION SYSTEM =====");
                System.out.println("1. Add Room");
                System.out.println("2. Add Customer");
                System.out.println("3. Make Reservation");
                System.out.println("4. Cancel Reservation");
                System.out.println("5. Display Rooms");
                System.out.println("6. Display Customers");
                System.out.println("7. Display Bookings");
                System.out.println("8. Display Bills");
                System.out.println("9. Exit");
                System.out.print("Choice: ");
                String input = sc.nextLine();
                switch (input)  {
                    case "1": addRoom();
                    break;
                    case "2": addCustomer();
                    break;
                    case "3": makeReservation();
                    break;
                    case "4": cancelReservation();
                    break;
                    case "5": displayRooms();
                    break;
                    case "6": displayCustomers();
                    break;
                    case "7": displayBookings();
                    break;
                    case "8": displayBills();
                    break;
                    case "9":
                    System.out.println("Thank you!");
                    return;
                    default:
                    System.out.println("Invalid choice.");
                }
            }
        }
    }
