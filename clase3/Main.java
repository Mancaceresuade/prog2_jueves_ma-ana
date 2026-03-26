//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        int[][] matriz = {{3,4,5},{1,2,3},{3,4,1}};
        System.out.println(algunaFilaTodosPares(matriz));
    }
    private static boolean algunaFilaTodosPares(int[][] matriz) {
        boolean rta = false; // 1
        for (int i = 0; i < matriz.length; i++) { // 1+3n
            rta = rta || todosPares(matriz[i]); // 2n+2*k(n) = 2n+n(3+8n)=2n+3n+8n**2
        }
        return rta; // 1
    } // f(n) = 1+3n+5n+8n**2+1= 2+8n+8n^2
    // Calculo de complejidad asintótica
    // f(n) < c g(n)
    // 2+8n+8n^2 < 9 n^2   // acotamos termino dominante , constante + 1
    // 2/n^2+8n/n^2+8n^2/n^2 < 9 n^2/n^2  // simplifico...
    // 2/n^2+8/n+8 < 9 // desde que valor de n0 se cumple esta condicion ?
    // no cumple para 1,2,... 8,
    // se cumple desde n0 >= 10
    // por lo tanto f(n) pertenece a O(n^2) desde n0 >= 10





    private static boolean todosPares(int[] lista) {
        boolean rta = true; // 1
        for (int i = 0; i < lista.length; i++) { // 1 + 2 * n + n
            rta = rta && esPar(lista[i]); // 2 * n + n * j(n) = 2n+3n = 5n
        }
        return rta; // 1
    } // k(n) = 1 + 1 + 2 * n + n + 5n + 1 = 3+8n

    private static boolean esPar(int i) {
        return (i%2)==0; // 3 instrucciones =>   constante O(1)
    } // j(n) = 3

}