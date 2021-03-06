package sanford.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URLEncoder;
import java.sql.*;
import java.util.ArrayList;

public class SQLServerHandler {
    private static Connection conn;
    private static String url;
    private static final Logger logger = LoggerFactory.getLogger("DatabaseLog");

    public SQLServerHandler(String host, String database, String user, String password) {
        try {
            //Added encoding due to some strings not being encoded
            user = URLEncoder.encode(user);
            password = URLEncoder.encode(password);
            url = "jdbc:mysql://" + host + "/" + database + "?user=" + user + "&password=" + password;
            openDBConnection();
            closeDBConnection();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    //////////////////////
    //Query Utils
    ///////////////////////
    private static ResultSet createResultSet(String query) throws SQLException {
        openDBConnection();
        Statement statement = conn.createStatement();
        return statement.executeQuery(query);
    }

    private static int getColumnCountFromResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData metadata = rs.getMetaData();
        return metadata.getColumnCount();
    }

    private static int executeSQLStatement(String query) throws SQLException {
            Statement statement = conn.createStatement();
            return statement.executeUpdate(query);
    }

    private static void openDBConnection() throws SQLException {
        conn = DriverManager.getConnection(url);
    }

    public static void closeDBConnection() throws SQLException {
        conn.close();
    }

    private static ArrayList<RoleDataContainer> getRoleDataContainers(String query) throws SQLException {
        ResultSet rs = createResultSet(query);
        int columnCount = getColumnCountFromResultSet(rs);
        ArrayList<RoleDataContainer> roleDataContainerList = new ArrayList<RoleDataContainer>();
        while (rs.next()) {
            StringBuilder row = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                row.append(rs.getString(i)).append(", ");
            }//end of for
            RoleDataContainer roleDataContainer = new RoleDataContainer(row.toString());
            roleDataContainerList.add(roleDataContainer);
        }//end of while
        closeDBConnection();
        return roleDataContainerList;
    }

    ////////////////////////////
    //Member Queries
    ///////////////////////////

    public static MemberDataContainer getMemberDataContainer(String memberID) throws SQLException {
        String query = "SELECT *\n" +
                "       FROM GuildMember\n" +
                "       WHERE GuildMember.ID = " + memberID;
        ResultSet rs = createResultSet(query);
        int columnCount = getColumnCountFromResultSet(rs);
        MemberDataContainer memberDataContainer = null;
        while (rs.next()) {
            StringBuilder row = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                row.append(rs.getString(i)).append(", ");
            }
            memberDataContainer = new MemberDataContainer(row.toString());
        }
        closeDBConnection();
        if(memberDataContainer == null){
            logger.warn("Could not find user " + memberID + " in the database. Creating a new user instead");
            memberDataContainer = new MemberDataContainer(memberID, 0);
            addMember(memberDataContainer);
        }

        return memberDataContainer;
    }

    public static void addMember(MemberDataContainer memberDataContainer) throws SQLException {
        openDBConnection();
        String query =  "INSERT INTO GuildMember (GuildMember.ID, GuildMember.Currency)\n" +
                            "VALUES (" +memberDataContainer.getId() + " ,"+ memberDataContainer.getCurrency() + ")";
        executeSQLStatement(query);
        closeDBConnection();
    }

    public static void addPointsToMember(ArrayList<String> ids, int value) throws SQLException {
        openDBConnection();
        for(String id : ids){
            try {
                String query = "UPDATE GuildMember " +
                        "SET GuildMember.Currency = GuildMember.Currency +" + value  +
                        " WHERE GuildMember.ID = '" +id + "'";
                if(executeSQLStatement(query) == 0){
                    MemberDataContainer memberDataContainer = new MemberDataContainer(id, value);
                    query =  "INSERT INTO GuildMember (GuildMember.ID, GuildMember.Currency)\n" +
                            "VALUES (" +memberDataContainer.getId() + " ,"+ memberDataContainer.getCurrency() + ")";
                    executeSQLStatement(query);
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.warn("Could not find user " + id + ". Adding user to the database.");
                MemberDataContainer memberDataContainer = new MemberDataContainer(id, value);
                try {
                    addMember(memberDataContainer);
                } catch (SQLException e) {
                    logger.error("Failed to register user with id " + id);
                }
            }
        }
        closeDBConnection();
    }

    public static ArrayList<MemberDataContainer> getTop10Members() throws SQLException {
        String query = " SELECT * "+
                "FROM GuildMember " +
                "ORDER BY GuildMember.Currency DESC " +
                "LIMIT 10 ";
        openDBConnection();
        ResultSet rs = createResultSet(query);
        int columnCount = getColumnCountFromResultSet(rs);
        ArrayList<MemberDataContainer> memberDataContainers = new ArrayList<MemberDataContainer>();
        while (rs.next()) {
            StringBuilder row = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                row.append(rs.getString(i)).append(", ");
            }//end of for
            MemberDataContainer memberDataContainer = new MemberDataContainer(row.toString());
            memberDataContainers.add(memberDataContainer);
        }
        closeDBConnection();
        return memberDataContainers;
    }

    public static void updateMembersCurrency(MemberDataContainer member) throws SQLException{
        openDBConnection();
        String query = "UPDATE GuildMember " +
                "SET GuildMember.Currency = " + member.getCurrency()  +
                " WHERE GuildMember.ID = '" + member.getId() + "'";
        executeSQLStatement(query);
        closeDBConnection();
    }


    ////////////////////////
    //Role Queries
    ///////////////////////

    public static ArrayList<RankDataContainer> getRanks() throws SQLException{
        String query = "SELECT * FROM Ranks ORDER BY Ranks.Tier ASC";
        openDBConnection();
        PreparedStatement statement = conn.prepareStatement(query);
        ResultSet results = statement.executeQuery();
        int columnCount = getColumnCountFromResultSet(results);
        ArrayList<RankDataContainer> rankList = new ArrayList<RankDataContainer>(7);
        while (results.next()){
            StringBuilder row = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                row.append(results.getString(i)).append(", ");
            }//end of for
             rankList.add(new RankDataContainer(row.toString()));
        }
        return rankList;
    }

    public static ArrayList<RoleDataContainer> getRoles() throws SQLException {
        String query = "SELECT * \n" +
                            "FROM Role "+
                            "ORDER BY Role.RoleTier ASC, Role.Cost DESC, Role.Name DESC";
        return getRoleDataContainers(query);
    }

    public static ArrayList<RoleDataContainer> getRolesForRankUp() throws SQLException {
        String query = "SELECT * \n" +
                "FROM Role "+
                "WHERE Role.RoleTier > 0 " +
                "ORDER BY Role.RoleTier ASC";
        return getRoleDataContainers(query);
    }


    public static String getRoleIDbyRoleName(String name)throws SQLException {
        String query = "SELECT RoleID " +
                " FROM RoleNames " +
                " WHERE RoleNames.RoleName = '" + name +"'";
        ResultSet rs = createResultSet(query);
        int columnCount = getColumnCountFromResultSet(rs);
        String solution;
        if(rs.next() == false){
            solution = "INVALID";
        }
        else{
            solution = rs.getString(1);
        }
        closeDBConnection();
        return solution;

    }

    public static RoleDataContainer getRoleByTier(int tier) throws SQLException {
        String query = "SELECT * \n" +
                "FROM Role " +
                "WHERE Role.RoleTier = '" + tier + "'";
        RoleDataContainer roleDataContainer = new RoleDataContainer();
        ResultSet rs = createResultSet(query);
        int columnCount = getColumnCountFromResultSet(rs);
        while (rs.next()) {
            StringBuilder row = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                row.append(rs.getString(i)).append(", ");
            }//end of for
            roleDataContainer = new RoleDataContainer(row.toString());
        }
        return  roleDataContainer;
    }
}